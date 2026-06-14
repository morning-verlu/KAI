# KAI Process Trace

`kaios trace` turns a saved run snapshot into a stable process trace.

The human-readable form is for terminals and screenshots:

```bash
kaios trace latest
```

The JSON form is the durable contract for CI, replay, audit logs, visualizers, and future Agent Desktop surfaces:

```bash
kaios trace latest --json
kaios trace latest --json --out artifacts/trace.json --force
```

Existing output files are protected by default. Pass `--force` when you intentionally want to overwrite a trace artifact.

Validate the trace contract without writing an artifact:

```bash
kaios trace latest --check
```

`--check` exits `0` when the generated trace satisfies the contract. It exits non-zero and prints specific contract issues when a saved run snapshot cannot produce a valid trace.

## Schema

Current schema id:

```text
kaios.process-trace/v1
```

Top-level fields:

| Field | Meaning |
| --- | --- |
| `schema` | Trace schema id. |
| `runId` | Saved KAI OS run id. |
| `workflowName` | Workflow that produced the run. |
| `task` | Original task summary saved with the run. |
| `success` | Whether the workflow completed successfully. |
| `metrics` | Aggregate process, token, context, syscall, duration, and event metrics. |
| `path` | Observed process path, ordered by PID. |
| `processes` | Per-agent process table with PID, state, tokens, context bytes, syscalls, duration, and failure. |
| `eventCounts` | Lifecycle event totals by event type. |
| `events` | Full lifecycle timeline. |

Example JSON shape:

```json
{
  "schema": "kaios.process-trace/v1",
  "runId": "run-97381ae9",
  "workflowName": "default",
  "success": true,
  "metrics": {
    "processCount": 3,
    "tokenTotal": 64,
    "syscallCount": 3,
    "eventCount": 18
  },
  "path": [
    "planner(pid=1)",
    "executor(pid=2)",
    "validator(pid=3)"
  ]
}
```

## Stability Rules

- New fields may be added to `kaios.process-trace/v1`.
- Existing field names keep their meaning within the same schema version.
- Breaking structural changes require a new schema id.
- Trace JSON is generated from `.kaios/runs/<run-id>.json`, so it can be produced without re-running agents.

## CI Pattern

Use `--trace-out` when a workflow run should become a build artifact without parsing `run_id` from stdout:

```bash
kaios run --index . --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
```

You can also generate a trace later from any saved run snapshot:

```bash
kaios trace latest --json --out artifacts/trace.json --force
```

Downstream checks can inspect `metrics.processCount`, `metrics.syscallCount`, `success`, `eventCounts`, or specific process states without scraping terminal output.

When you need a full portable evidence package, use a run capsule. It embeds the saved snapshot, generated trace, provenance hashes, validation status, and replay commands:

```bash
kaios capsule latest
kaios capsule latest --check
kaios capsule --file artifacts/run.capsule.json --check
```

Capsule JSON uses schema `kaios.run-capsule/v1`; see [CAPSULE.md](CAPSULE.md).

For a simple gate, validate the contract directly:

```bash
kaios trace latest --check
```

Successful output is intentionally short:

```text
trace: run-97381ae9
schema: kaios.process-trace/v1
status: valid
processes: 3
events: 18
```
