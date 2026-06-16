#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${KAIOS_DOCKER_IMAGE:-kaios:local}"
WORKDIR="${KAIOS_DOCKER_SMOKE_DIR:-}"
BUILD_IMAGE="${KAIOS_DOCKER_BUILD:-1}"
PREFLIGHT_ONLY=0
WORKDIR_IS_TEMP=0

usage() {
  cat <<'USAGE'
Usage: ./scripts/docker-smoke.sh [--image IMAGE] [--workdir DIR] [--no-build] [--preflight]

Builds and verifies the KAI OS Docker trial image by default.

Options:
  --image IMAGE   Image tag to build or reuse. Defaults to kaios:local.
  --workdir DIR   Host directory for generated smoke artifacts.
  --no-build      Reuse an existing image instead of running docker build.
  --preflight     Run Dockerfile and base-image checks without building the image.

Environment:
  KAIOS_DOCKER_IMAGE       Default image tag.
  KAIOS_DOCKER_SMOKE_DIR   Default smoke artifact directory.
  KAIOS_DOCKER_BUILD=0     Reuse an existing image.
  KAIOS_KEEP_SMOKE=1       Keep generated smoke artifacts.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --image)
      IMAGE="${2:-}"
      if [[ -z "$IMAGE" ]]; then
        echo "--image requires a value." >&2
        exit 1
      fi
      shift 2
      ;;
    --workdir)
      WORKDIR="${2:-}"
      if [[ -z "$WORKDIR" ]]; then
        echo "--workdir requires a value." >&2
        exit 1
      fi
      shift 2
      ;;
    --no-build|--skip-build)
      BUILD_IMAGE=0
      shift
      ;;
    --preflight|--check-only)
      PREFLIGHT_ONLY=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "$WORKDIR" ]]; then
  WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/kaios-docker-smoke.XXXXXX")"
  WORKDIR_IS_TEMP=1
fi

if [[ -z "${KAIOS_KEEP_SMOKE:-}" && "$WORKDIR_IS_TEMP" == "1" ]]; then
  trap 'rm -rf "$WORKDIR"' EXIT
fi

assert_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Expected file '$path' to exist." >&2
    exit 1
  fi
}

assert_contains() {
  local path="$1"
  local expected="$2"
  if ! grep -Fq -- "$expected" "$path"; then
    echo "Expected '$path' to contain: $expected" >&2
    exit 1
  fi
}

run_step() {
  local label="$1"
  shift
  echo "==> $label" >&2
  "$@"
}

mkdir -p "$WORKDIR"
WORKDIR="$(cd "$WORKDIR" && pwd)"

if [[ "$PREFLIGHT_ONLY" == "1" ]]; then
  BASE_IMAGE="$(awk 'toupper($1) == "FROM" { print $2; exit }' "$ROOT/Dockerfile")"
  if [[ -z "$BASE_IMAGE" ]]; then
    echo "Could not find a FROM image in Dockerfile." >&2
    exit 1
  fi

  run_step "check Dockerfile" docker build --check "$ROOT"
  run_step "inspect Docker base image manifest" docker manifest inspect "$BASE_IMAGE" > "$WORKDIR/base-manifest.json"
  assert_contains "$WORKDIR/base-manifest.json" '"os": "linux"'
  assert_contains "$WORKDIR/base-manifest.json" '"architecture": "amd64"'
  assert_contains "$WORKDIR/base-manifest.json" '"architecture": "arm64"'

  echo "kaios docker preflight ok"
  echo "base_image: $BASE_IMAGE"
  if [[ -n "${KAIOS_KEEP_SMOKE:-}" || -n "${KAIOS_DOCKER_SMOKE_DIR:-}" ]]; then
    echo "smoke_workspace: $WORKDIR"
  fi
  exit 0
fi

if [[ "$BUILD_IMAGE" == "0" || "$BUILD_IMAGE" == "false" ]]; then
  run_step "reuse existing Docker image" docker image inspect "$IMAGE" >/dev/null
else
  run_step "build Docker image" docker build -t "$IMAGE" "$ROOT"
fi

run_step "print CLI version from image" docker run --rm "$IMAGE" --version > "$WORKDIR/version.out"
assert_contains "$WORKDIR/version.out" "kaios"

run_step "run no-key tour in Docker" docker run --rm -v "$WORKDIR:/work" "$IMAGE" tour --dir /work/tour --json > "$WORKDIR/tour.json"
assert_contains "$WORKDIR/tour.json" "\"schema\": \"kaios.tour/v1\""

TOUR_CAPSULE="$WORKDIR/tour/workspace/artifacts/change-review.capsule.json"
assert_file "$TOUR_CAPSULE"

run_step "validate generated Docker tour capsule" docker run --rm -v "$WORKDIR:/work" "$IMAGE" capsule --file /work/tour/workspace/artifacts/change-review.capsule.json --check > "$WORKDIR/tour-capsule.out"
assert_contains "$WORKDIR/tour-capsule.out" "status: valid"

run_step "replay generated Docker tour capsule offline" docker run --rm -v "$WORKDIR:/work" "$IMAGE" replay --file /work/tour/workspace/artifacts/change-review.capsule.json > "$WORKDIR/tour-replay.out"
assert_contains "$WORKDIR/tour-replay.out" "status: valid"
assert_contains "$WORKDIR/tour-replay.out" "deterministic: true"

run_step "validate bundled Evidence Sample capsule" docker run --rm "$IMAGE" capsule --file examples/evidence-sample/change-review.capsule.json --check > "$WORKDIR/sample-capsule.out"
assert_contains "$WORKDIR/sample-capsule.out" "status: valid"

echo "kaios docker smoke ok"
echo "image: $IMAGE"
if [[ -n "${KAIOS_KEEP_SMOKE:-}" || -n "${KAIOS_DOCKER_SMOKE_DIR:-}" ]]; then
  echo "smoke_workspace: $WORKDIR"
fi
