# Local Tour

Use the local tour when you want to feel what KAI OS does before reading the full docs.

The tour runs entirely on your machine with the deterministic provider. It does not need an API key.

If you installed the CLI, start with the built-in disposable tour:

```bash
kaios tour
```

`kaios tour` creates a tiny temporary Git project, runs quickstart, makes a code change, runs review, writes a process trace and capsule, and prints next commands for inspection.

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
