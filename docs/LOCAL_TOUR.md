# Local Tour

Use the local tour when you want to feel what KAI OS does before reading the full docs.

The tour runs entirely on your machine with the deterministic provider. It does not need an API key.

If you installed the CLI, start with the built-in disposable tour:

```bash
kaios tour
```

`kaios tour` creates a tiny temporary Git project, runs quickstart, makes a code change, runs review, writes a process trace and capsule, and prints next commands for inspection.

## 60-Second Tour Transcript

Here is what you will see in your terminal when running the tour (paths will show your operating system's temp directory):

```text
KAI OS Evidence OS tour
schema: kaios.tour/v1
status: ready
run_id: run-89d4fb12
workspace: /tmp/kaios-tour.849204/workspace
changed_file: src/App.kt

what happened:
  1. Created a disposable Git workspace.
  2. Ran no-key quickstart with the deterministic provider.
  3. Changed src/App.kt and reviewed the diff.
  4. Wrote a Markdown review, process trace, replayable capsule, and recovery dry-run report.

product proof:
  Agent = Process   -> run 'kaios ps run-89d4fb12'
  Tool  = Syscall   -> inspect 'artifacts/change-review.trace.json'
  Run   = Evidence  -> replay 'artifacts/change-review.capsule.json'
  CI    = Gate      -> compare a future baseline with 'kaios evidence --baseline ... --check'

artifacts:
  review: /tmp/kaios-tour.849204/workspace/artifacts/change-review.md
  trace: /tmp/kaios-tour.849204/workspace/artifacts/change-review.trace.json
  capsule: /tmp/kaios-tour.849204/workspace/artifacts/change-review.capsule.json

commands executed:
  quickstart: exit=0 command="kaios quickstart --no-ci --json"
  review: exit=0 command="kaios review --json"
  ps: exit=0 command="kaios ps run-89d4fb12 --json"
  evidence: exit=0 command="kaios evidence run-89d4fb12 --summary"
  recover: exit=0 command="kaios recover run-89d4fb12 --dry-run --json"
```

### Artifacts to Inspect

Once the tour wraps up, head over to the printed `workspace` directory. You will find three main files worth looking at:

1. **Review Artifact** (`artifacts/change-review.md`) — A readable Markdown summary of what the agent actually did. It includes a process table and a timeline of events.
2. **Process Trace** (`artifacts/change-review.trace.json`) — The detailed JSON trace behind the run. This tracks token counts, process states, and the **syscall ledger** (which lists every tool call the agent tried to make).
3. **Replay Capsule** (`artifacts/change-review.capsule.json`) — A standalone bundle containing the run's snapshot and trace. You can copy this to another machine to replay the run offline without needing a model provider.

---

Use the source-tree script when developing KAI OS itself or when you want to run the tour against an existing local project:

```bash
./scripts/local-tour.sh
```

What it shows:

- `kaios next` chooses the next workspace-aware action.
- `kaios analyze . --format json` maps the project without a model call.
- `kaios run --index . --context README.md` creates a project-aware agent run.
- `kaios ps <run-id>` shows agents as OS-style processes.
- `kaios trace <run-id> --check` validates the process trace contract.
- `kaios evidence <run-id>` packages a portable capsule.
- `kaios replay --file <capsule>` proves the capsule can rebuild the trace offline.

The script writes handoff files to a temp directory and prints their paths:

```text
artifact: /tmp/kaios-tour.xxxxxx/project.md
trace: /tmp/kaios-tour.xxxxxx/trace.json
capsule: /tmp/kaios-tour.xxxxxx/run.capsule.json
analysis: /tmp/kaios-tour.xxxxxx/analysis.json
```

It also isolates KAI OS runtime state by default:

```text
runtime_state: /tmp/kaios-tour.xxxxxx/runtime/runs
```

Run the same tour against another local project:

```bash
KAIOS_TOUR_WORKDIR=/path/to/project ./scripts/local-tour.sh "summarize this project"
```

If the target project has no README, the tour omits `--context` and still runs with Workspace Index only.

Use a specific installed CLI:

```bash
KAIOS_BIN=/usr/local/bin/kaios ./scripts/local-tour.sh
```

Keep the output in a predictable directory:

```bash
KAIOS_TOUR_DIR=/tmp/kaios-tour ./scripts/local-tour.sh
```

The target workspace is left alone by default: run snapshots, reports, generated runtime artifacts, and generated capsules are redirected into the tour directory through `KAIOS_RUNS_DIR`, `KAIOS_REPORTS_DIR`, `KAIOS_ARTIFACTS_DIR`, and `KAIOS_CAPSULES_DIR`. The tour will not create `.kaios/` inside the target project unless you override those environment variables yourself.

If you explicitly set those environment variables yourself, the tour respects them.
