#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KAIOS_BIN="${KAIOS_BIN:-$ROOT/build/install/kaios-cli/bin/kaios}"
WORKDIR="${KAIOS_TOUR_WORKDIR:-$ROOT}"
TOUR_DIR="${KAIOS_TOUR_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/kaios-tour.XXXXXX")}"
TASK="${1:-summarize this project}"

if [[ ! -x "$KAIOS_BIN" ]]; then
  echo "kaios local tour: building local installDist first" >&2
  (cd "$ROOT" && ./gradlew installDist)
fi

find_readme() {
  local wanted path lower
  for wanted in readme.md readme.markdown readme; do
    for path in *; do
      [[ -f "$path" ]] || continue
      lower="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
      if [[ "$lower" == "$wanted" ]]; then
        printf '%s\n' "$path"
        return 0
      fi
    done
  done
  return 1
}

announce() {
  local label="$1"
  echo
  echo "==> $label"
}

mkdir -p "$TOUR_DIR"
cd "$WORKDIR"

README_CONTEXT="$(find_readme || true)"

ARTIFACT="$TOUR_DIR/project.md"
TRACE="$TOUR_DIR/trace.json"
CAPSULE="$TOUR_DIR/run.capsule.json"

export KAIOS_RUNS_DIR="${KAIOS_RUNS_DIR:-$TOUR_DIR/runtime/runs}"
export KAIOS_REPORTS_DIR="${KAIOS_REPORTS_DIR:-$TOUR_DIR/runtime/reports}"
export KAIOS_ARTIFACTS_DIR="${KAIOS_ARTIFACTS_DIR:-$TOUR_DIR/runtime/artifacts}"
export KAIOS_CAPSULES_DIR="${KAIOS_CAPSULES_DIR:-$TOUR_DIR/runtime/capsules}"

echo "KAI OS local tour"
echo "workspace: $WORKDIR"
echo "outputs: $TOUR_DIR"
echo "runtime_state: $KAIOS_RUNS_DIR"
echo "task: $TASK"
if [[ -n "$README_CONTEXT" ]]; then
  echo "context: $README_CONTEXT"
else
  echo "context: none; using Workspace Index only"
fi

announce "kaios --version"
"$KAIOS_BIN" --version

announce "kaios next --json"
"$KAIOS_BIN" next --json > "$TOUR_DIR/next.json"
sed -n '1,28p' "$TOUR_DIR/next.json"

announce "kaios analyze . --format json"
"$KAIOS_BIN" analyze . --format json > "$TOUR_DIR/analysis.json"
grep -E '"files"|"lines"|"bytes"|"language"' "$TOUR_DIR/analysis.json" | sed -n '1,14p'

announce "kaios run --index . ${README_CONTEXT:+--context $README_CONTEXT}"
RUN_COMMAND=("$KAIOS_BIN" run --index .)
if [[ -n "$README_CONTEXT" ]]; then
  RUN_COMMAND+=(--context "$README_CONTEXT")
fi
RUN_COMMAND+=(--out "$ARTIFACT" --trace-out "$TRACE" --force "$TASK")
"${RUN_COMMAND[@]}" > "$TOUR_DIR/run.out"
sed -n '1,34p' "$TOUR_DIR/run.out"

RUN_ID="$(awk '/^run_id:/ { print $2 }' "$TOUR_DIR/run.out")"
if [[ -z "$RUN_ID" ]]; then
  echo "Could not find run_id in $TOUR_DIR/run.out" >&2
  exit 1
fi

announce "kaios ps $RUN_ID"
"$KAIOS_BIN" ps "$RUN_ID" | tee "$TOUR_DIR/ps.out"
announce "kaios trace $RUN_ID --check"
"$KAIOS_BIN" trace "$RUN_ID" --check | tee "$TOUR_DIR/trace-check.out"
announce "kaios evidence $RUN_ID"
"$KAIOS_BIN" evidence "$RUN_ID" --out "$CAPSULE" --force | tee "$TOUR_DIR/evidence.out"
announce "kaios replay --file $CAPSULE"
"$KAIOS_BIN" replay --file "$CAPSULE" | tee "$TOUR_DIR/replay.out"

echo
echo "Local tour complete"
echo "run_id: $RUN_ID"
echo "artifact: $ARTIFACT"
echo "trace: $TRACE"
echo "capsule: $CAPSULE"
echo "analysis: $TOUR_DIR/analysis.json"
echo "next: $TOUR_DIR/next.json"
