#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KAIOS_BIN="${KAIOS_BIN:-$ROOT/build/install/kaios-cli/bin/kaios}"
WORKDIR="${KAIOS_REPOSITORY_CI_SMOKE_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/kaios-repository-ci.XXXXXX")}"

if [[ -z "${KAIOS_KEEP_SMOKE:-}" && -z "${KAIOS_REPOSITORY_CI_SMOKE_DIR:-}" ]]; then
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

echo "==> build, test, and install CLI" >&2
(cd "$ROOT" && ./gradlew clean test installDist --no-daemon)

if [[ ! -x "$KAIOS_BIN" ]]; then
  echo "Expected executable '$KAIOS_BIN' after installDist." >&2
  exit 1
fi

run_step "print CLI version" "$KAIOS_BIN" --version > "$WORKDIR/version.out"
assert_contains "$WORKDIR/version.out" "kaios"

run_step "validate checked-in evidence samples" env KAIOS_BIN="$KAIOS_BIN" "$ROOT/scripts/evidence-samples-smoke.sh"

TOUR_DIR="$WORKDIR/tour"
TOUR_WORKSPACE="$TOUR_DIR/workspace"
TOUR_CAPSULE="$TOUR_WORKSPACE/artifacts/change-review.capsule.json"

run_step "run no-key tour" "$KAIOS_BIN" tour --dir "$TOUR_DIR" --json > "$WORKDIR/tour.json"
assert_contains "$WORKDIR/tour.json" "\"schema\": \"kaios.tour/v1\""
assert_file "$TOUR_CAPSULE"

run_step "validate tour capsule" "$KAIOS_BIN" capsule --file "$TOUR_CAPSULE" --check > "$WORKDIR/tour-capsule.out"
assert_contains "$WORKDIR/tour-capsule.out" "status: valid"

run_step "replay tour capsule offline" "$KAIOS_BIN" replay --file "$TOUR_CAPSULE" > "$WORKDIR/tour-replay.out"
assert_contains "$WORKDIR/tour-replay.out" "status: valid"
assert_contains "$WORKDIR/tour-replay.out" "deterministic: true"

echo "kaios repository ci smoke ok"
if [[ -n "${KAIOS_KEEP_SMOKE:-}" || -n "${KAIOS_REPOSITORY_CI_SMOKE_DIR:-}" ]]; then
  echo "smoke_workspace: $WORKDIR"
fi
