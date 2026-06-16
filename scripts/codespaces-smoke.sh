#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KAIOS_BIN="${KAIOS_BIN:-$ROOT/build/install/kaios-cli/bin/kaios}"
WORKDIR="${KAIOS_CODESPACES_SMOKE_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/kaios-codespaces-smoke.XXXXXX")}"

if [[ -z "${KAIOS_KEEP_SMOKE:-}" && -z "${KAIOS_CODESPACES_SMOKE_DIR:-}" ]]; then
  trap 'rm -rf "$WORKDIR"' EXIT
fi

run_step() {
  local label="$1"
  shift
  echo "==> $label" >&2
  "$@"
}

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

mkdir -p "$WORKDIR"

echo "==> build local CLI" >&2
(cd "$ROOT" && ./gradlew installDist --no-daemon)

if [[ ! -x "$KAIOS_BIN" ]]; then
  echo "Expected executable '$KAIOS_BIN' after installDist." >&2
  exit 1
fi

TOUR_DIR="$WORKDIR/tour"
run_step "run no-key Evidence OS tour" "$KAIOS_BIN" tour --dir "$TOUR_DIR" --json > "$WORKDIR/tour.json"
assert_contains "$WORKDIR/tour.json" "\"schema\": \"kaios.tour/v1\""

TOUR_WORKSPACE="$TOUR_DIR/workspace"
TOUR_CAPSULE="$TOUR_WORKSPACE/artifacts/change-review.capsule.json"
TOUR_TRACE="$TOUR_WORKSPACE/artifacts/change-review.trace.json"
TOUR_ARTIFACT="$TOUR_WORKSPACE/artifacts/change-review.md"

assert_file "$TOUR_CAPSULE"
assert_file "$TOUR_TRACE"
assert_file "$TOUR_ARTIFACT"

run_step "validate generated tour capsule" "$KAIOS_BIN" capsule --file "$TOUR_CAPSULE" --check > "$WORKDIR/tour-capsule.out"
assert_contains "$WORKDIR/tour-capsule.out" "status: valid"

run_step "replay generated tour capsule offline" "$KAIOS_BIN" replay --file "$TOUR_CAPSULE" > "$WORKDIR/tour-replay.out"
assert_contains "$WORKDIR/tour-replay.out" "status: valid"
assert_contains "$WORKDIR/tour-replay.out" "deterministic: true"

EVIDENCE_SAMPLE="$ROOT/examples/evidence-sample/change-review.capsule.json"
assert_file "$EVIDENCE_SAMPLE"

run_step "validate checked-in Evidence Sample" "$KAIOS_BIN" capsule --file "$EVIDENCE_SAMPLE" --check > "$WORKDIR/sample-capsule.out"
assert_contains "$WORKDIR/sample-capsule.out" "status: valid"

run_step "replay checked-in Evidence Sample offline" "$KAIOS_BIN" replay --file "$EVIDENCE_SAMPLE" > "$WORKDIR/sample-replay.out"
assert_contains "$WORKDIR/sample-replay.out" "status: valid"
assert_contains "$WORKDIR/sample-replay.out" "deterministic: true"

echo "kaios codespaces smoke ok"
echo "tour_workspace: $TOUR_WORKSPACE"
echo "tour_artifact: $TOUR_ARTIFACT"
echo "tour_trace: $TOUR_TRACE"
echo "tour_capsule: $TOUR_CAPSULE"
if [[ -n "${KAIOS_KEEP_SMOKE:-}" || -n "${KAIOS_CODESPACES_SMOKE_DIR:-}" ]]; then
  echo "smoke_workspace: $WORKDIR"
fi
