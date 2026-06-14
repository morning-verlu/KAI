#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KAIOS_BIN="${KAIOS_BIN:-$ROOT/build/install/kaios-cli/bin/kaios}"

if [[ ! -x "$KAIOS_BIN" ]]; then
  echo "kaios quickstart smoke: building local installDist first" >&2
  (cd "$ROOT" && ./gradlew installDist)
fi

if [[ -n "${KAIOS_SMOKE_DIR:-}" ]]; then
  WORKDIR="$KAIOS_SMOKE_DIR"
  CLEANUP=0
else
  WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/kaios-quickstart.XXXXXX")"
  CLEANUP=1
fi

if [[ "$CLEANUP" == 1 && -z "${KAIOS_KEEP_SMOKE:-}" ]]; then
  trap 'rm -rf "$WORKDIR"' EXIT
fi

mkdir -p "$WORKDIR"
cd "$WORKDIR"

cat > README.md <<'MARKDOWN'
# Quickstart Smoke Project

Small local project used to verify the KAI OS first-run path.
MARKDOWN

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

run_step "kaios quickstart" "$KAIOS_BIN" quickstart > quickstart.out
assert_contains quickstart.out "KAI OS quickstart"
assert_contains quickstart.out "schema: kaios.quickstart/v1"
assert_contains quickstart.out "status: ready"
assert_contains quickstart.out "demo: ready"
assert_contains quickstart.out "setup: ready config=created ci=created"
assert_contains quickstart.out "verify: ready"
assert_contains quickstart.out "ci_artifact: kaios-agent-gate"
assert_contains quickstart.out "ci_artifact_paths: artifacts/kaios-verify.json, artifacts/kaios-run.capsule.json, artifacts/kaios-bug-report.json"
assert_contains quickstart.out "ci_push_note: Pushing .github/workflows/kaios.yml may require GitHub workflow permission/scope."
assert_contains quickstart.out "evidence_capsule:"
assert_file kaios.json
assert_file .github/workflows/kaios.yml
assert_file .kaios/artifacts/kaios-quickstart.capsule.json
assert_contains .github/workflows/kaios.yml "kaios verify --config 'kaios.json' --evidence --json --force | tee artifacts/kaios-verify.json"
assert_contains .github/workflows/kaios.yml "kaios bug-report --config 'kaios.json' --json --out artifacts/kaios-bug-report.json --force"
assert_contains .github/workflows/kaios.yml "name: kaios-agent-gate"

run_step "kaios quickstart --json" "$KAIOS_BIN" quickstart --json > quickstart.json
assert_contains quickstart.json '"schema": "kaios.quickstart/v1"'
assert_contains quickstart.json '"status": "ready"'

mkdir -p local-only
(
  cd local-only
  cat > README.md <<'MARKDOWN'
# Local Quickstart Smoke

Small local-only project used to verify quickstart without CI files.
MARKDOWN
  run_step "kaios quickstart --no-ci" "$KAIOS_BIN" quickstart --no-ci > quickstart-local.out
  assert_contains quickstart-local.out "schema: kaios.quickstart/v1"
  assert_contains quickstart-local.out "status: ready"
  assert_contains quickstart-local.out "setup: ready config=created ci=skipped"
  assert_contains quickstart-local.out "git add kaios.json"
  assert_file kaios.json
  if [[ -f .github/workflows/kaios.yml ]]; then
    echo "Expected local-only quickstart not to write .github/workflows/kaios.yml." >&2
    exit 1
  fi
)

run_step "kaios gate" "$KAIOS_BIN" gate > gate.out
assert_contains gate.out "KAI OS gate"
assert_contains gate.out "status: ready"
assert_contains gate.out "evidence: valid"
assert_file artifacts/kaios-run.capsule.json

run_step "kaios run --index . --context README.md" "$KAIOS_BIN" run \
  --index . \
  --context README.md \
  --out artifacts/project.md \
  --trace-out artifacts/trace.json \
  --force \
  "summarize this project" > run.out
assert_contains run.out "success: true"
assert_file artifacts/project.md
assert_file artifacts/trace.json

run_step "kaios ps" "$KAIOS_BIN" ps > ps.out
assert_contains ps.out "RUN "
assert_contains ps.out "PID"

run_step "kaios trace --check" "$KAIOS_BIN" trace --check > trace-check.out
assert_contains trace-check.out "status: valid"

echo "kaios quickstart smoke ok"
if [[ "$CLEANUP" == 0 || -n "${KAIOS_KEEP_SMOKE:-}" ]]; then
  echo "workspace: $WORKDIR"
fi
