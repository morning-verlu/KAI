# Local Tour

Use the local tour when you want to feel what KAI OS does before reading the full docs.

The tour runs entirely on your machine with the deterministic provider. It does not need an API key.

If you installed the CLI, start with the built-in disposable tour:

```bash
kaios tour
```

`kaios tour` creates a tiny temporary Git project, runs quickstart, makes a code change, runs review, writes a process trace and capsule, and prints next commands for inspection.

## 60-Second Tour Transcript

If you want to see what `kaios tour` actually prints before installing, this is the section to read. The tour is short, deterministic, and produces a single block of text you can scan in under a minute.

> **Note:** Run-id and path values below are placeholders. On your machine the run-id will be a fresh short hash, and the absolute paths will reflect whatever you pass to `--dir` (or the temp directory KAI OS picks by default).

```text
KAI OS Evidence OS tour
schema: kaios.tour/v1
status: ready
run_id: <run-id>
workspace: <workspace>
changed_file: src/App.kt

what happened:
  1. Created a disposable Git workspace.
  2. Ran no-key quickstart with the deterministic provider.
  3. Changed src/App.kt and reviewed the diff.
  4. Wrote a Markdown review, process trace, replayable capsule, and recovery dry-run report.

product proof:
  Agent = Process   -> run 'kaios ps <run-id>'
  Tool  = Syscall   -> inspect '<path-to-trace>'
  Run   = Evidence  -> replay '<path-to-capsule>'
  CI    = Gate      -> compare a future baseline with 'kaios evidence --baseline ... --check'

artifacts:
  review: <path-to-artifact>
  trace:  <path-to-trace>
  capsule: <path-to-capsule>

next:
  cd <workspace>
  kaios ps <run-id>
  kaios inspect <run-id>
  kaios trace <run-id> --check
  kaios replay --file <path-to-capsule>
```

What to notice in the transcript:

- **`product proof:` is the payoff.** The four-line mapping (`Agent = Process`, `Tool = Syscall`, `Run = Evidence`, `CI = Gate`) is the whole point of KAI OS in one screen. If those four lines land, the rest of the tour is window dressing.
- **`status: ready` means exit code 0.** If you see anything other than `ready`, something in the disposable workspace failed and the tour did not produce usable evidence — re-run with a fresh `--dir` to retry.
- **`artifacts:` points at three files you should open.** The review Markdown is the human-readable summary; the trace is the structured process/agent log; the capsule is the portable, replayable bundle. Together they are what "evidence" means here.
- **`next:` gives you the next five commands.** Each one inspects the run you just generated, in order from cheapest to most expensive. You do not need to run all five — `kaios ps <run-id>` alone shows you the OS-style process table.

### Inspect These Artifacts

The three artifact paths the tour prints are the things you actually want to look at after the run:

- **`<path-to-artifact>`** — the Markdown review of the change (`change-review.md`). Read this first; it is the closest thing the tour has to a human summary.
- **`<path-to-trace>`** — the process trace (`change-review.trace.json`). Open it in any JSON viewer to see the per-agent process rows, syscalls, and lifecycle events.
- **`<path-to-capsule>`** — the replayable capsule (`change-review.capsule.json`). This is the one you can ship to a reviewer or replay offline without re-running the agent: `kaios replay --file <path-to-capsule>`.

All three live under the `--dir` you passed to the tour (or under the temp directory KAI OS picked for you). They do not appear in the tour's working tree — the tour redirects generated artifacts into the `--dir` so your real project stays clean.

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
