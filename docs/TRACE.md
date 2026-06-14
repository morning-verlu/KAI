# KAI Process Trace

`kaios trace` turns a saved run snapshot into a stable process trace.

The human-readable form is for terminals and screenshots:

```bash
kaios trace <run-id>
```

The JSON form is the durable contract for CI, replay, audit logs, visualizers, and future Agent Desktop surfaces:

```bash
kaios trace <run-id> --json
kaios trace <run-id> --json --out artifacts/trace.json --force
```

Existing output files are protected by default. Pass `--force` when you intentionally want to overwrite a trace artifact.

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

Use trace files when a workflow run should become a build artifact:

```bash
kaios run --index . --out artifacts/project.md --force "summarize this project"
kaios trace <run-id> --json --out artifacts/trace.json --force
```

Downstream checks can inspect `metrics.processCount`, `metrics.syscallCount`, `success`, `eventCounts`, or specific process states without scraping terminal output.
