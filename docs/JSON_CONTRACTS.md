# JSON Contracts

KAI OS commands that print JSON are intended for CI, dashboards, issue triage, future Agent Desktop views, and local automation.

The rule of thumb is simple:

- Use `schema` or `schemaVersion` to identify the payload before reading fields.
- Use stable fields for gates and dashboards.
- Use `next` for human-readable command lists.
- Use `nextActions` when a tool needs stable action ids instead of parsing shell text.

## Contract Matrix

| Command | Schema | Primary Use |
| --- | --- | --- |
| `kaios quickstart --json` | `kaios.quickstart/v1` | One-command onboarding state across demo, setup, verify, and evidence. |
| `kaios setup --json` | `kaios.setup/v1` | Bootstrap state, generated files, validation, and next actions. |
| `kaios verify --json` | `kaios.verify/v1` | One-command local and CI readiness gate. |
| `kaios config validate --json` | `kaios.config-validation/v1` | Workflow config validation without starting agents. |
| `kaios doctor --json` | `kaios.doctor/v1` | Machine-readable environment diagnostics. |
| `kaios bug-report --json` | `kaios.bug-report/v1` | Safe support bundle for issues and handoff. |
| `kaios runs --json` | `kaios.runs/v1` | Saved run registry for local tooling and UI lists. |
| `kaios trace <run> --json` | `kaios.process-trace/v1` | Process metrics, path, and lifecycle timeline. |
| `kaios capsule <run> --json` | `kaios.run-capsule/v1` | Portable run package with snapshot, trace, and provenance. |
| `kaios replay --file capsule.json --json` | `kaios.run-replay/v1` | Offline capsule replay and deterministic trace rebuild checks. |
| `kaios diff left.capsule.json right.capsule.json --json` | `kaios.run-diff/v1` | Stable run comparison for regression gates. |
| `kaios evidence <run> --json` | `kaios.evidence/v1` | Capsule packaging, validation, replay, optional diff, and next actions. |
| `kaios analyze . --format json` | `schemaVersion: 1` | No-key workspace analysis report. |

## Stability Rules

- New fields may be added to an existing schema.
- Existing field names keep their meaning within the same schema id.
- Breaking structural changes require a new schema id.
- Consumers should ignore unknown fields.
- Consumers should prefer booleans and status fields over parsing terminal text.
- Secrets are not intentionally emitted by `doctor` or `bug-report`; do not add secrets manually when filing issues.

## Shared Next Actions

These schemas include both `next` and `nextActions`:

- `kaios.setup/v1`
- `kaios.verify/v1`
- `kaios.quickstart/v1`
- `kaios.config-validation/v1`
- `kaios.doctor/v1`
- `kaios.bug-report/v1`
- `kaios.evidence/v1`

`next` is a list of shell commands for people and existing scripts:

```json
{
  "next": [
    "kaios ps latest",
    "kaios evidence latest",
    "kaios bug-report"
  ]
}
```

`nextActions` gives automation a stable id, the same command, and a short reason:

```json
{
  "nextActions": [
    {
      "id": "package-evidence",
      "command": "kaios evidence latest",
      "reason": "Package, validate, replay, and optionally diff run evidence."
    }
  ]
}
```

The `command` value in every `nextActions` item is also present in `next`.

## Stable Action Ids

| Action Id | Meaning |
| --- | --- |
| `fix-failed-checks` | Resolve failed diagnostics before retrying. |
| `repair-config` | Repair or regenerate a workflow config. |
| `stage-generated-files` | Stage generated config and CI files. |
| `quickstart` | Run the no-key onboarding gate and create inspectable evidence. |
| `setup-project` | Create a validated workflow and optional CI gate. |
| `validate-config` | Validate workflow config without running agents. |
| `show-config` | Inspect agents, tools, dependencies, and fallback routes. |
| `verify-project` | Run the readiness gate and optionally write evidence. |
| `run-demo` | Create a no-key run snapshot for inspection and support. |
| `run-workflow` | Run an inspectable agent workflow. |
| `analyze-workspace` | Generate a deterministic workspace report. |
| `show-processes` | Inspect agent process metrics. |
| `inspect-run` | Read final output and lifecycle events. |
| `check-trace` | Validate a saved process trace. |
| `view-trace` | Inspect a saved process trace. |
| `package-evidence` | Package, validate, replay, and optionally diff evidence. |
| `compare-evidence` | Compare evidence against a baseline capsule. |
| `validate-capsule` | Validate a portable run capsule. |
| `replay-capsule` | Replay a capsule offline. |
| `diff-capsules` | Compare two capsules with stable runtime signatures. |
| `collect-support-report` | Generate a safe support bundle. |
| `run-diagnostics` | Run machine-readable local diagnostics. |
| `next` | Generic fallback for uncategorized commands. |

## Recommended Gates

### Onboarding Gate

Use `kaios.quickstart/v1` when automation or docs need to prove the first-run path works:

```bash
kaios quickstart --json
```

Gate on:

- `status == "ready"`
- `demo.success == true`
- `setup.validation.valid == true`
- `verify.status == "ready"`
- `verify.evidence.valid == true`
- `errors` is empty

Read `nextActions` to send the user to process inspection, trace validation, or the first project run without parsing terminal text.

### Bootstrap Gate

Use `kaios.setup/v1` when automation needs to know what setup wrote:

```bash
kaios setup --ci --json
```

Read:

- `config.path` and `config.action` for the project workflow file.
- `ci.path` and `ci.action` for the generated GitHub Actions Agent Gate.
- `ciArtifact.name` and `ciArtifact.paths` for the uploaded `kaios-agent-gate` bundle when CI is enabled.
- `validation.valid == true` before committing the generated workflow.

### Readiness Gate

Use `kaios.verify/v1` when CI needs one answer:

```bash
kaios setup --ci
kaios verify --json
```

Gate on:

- `status == "ready"`
- `config.valid == true`
- `run.success == true`
- `trace.valid == true`
- `errors` is empty

### Evidence Gate

Use `kaios.evidence/v1` when CI needs a portable proof artifact:

```bash
kaios verify --evidence --json --force
```

Gate on:

- `status == "valid"`
- `valid == true`
- `capsule.valid == true`
- `replay.valid == true`
- `diff.valid == true`
- `diff.status == "same"` when a baseline is required to match

When running with `--check` and a baseline, KAI OS exits `1` for a valid-but-different result and `2` for invalid evidence.

### Config Gate

Use `kaios.config-validation/v1` when a job only needs to check workflow files:

```bash
kaios config validate --config workflows/research.json --json
```

Gate on:

- `valid == true`
- `errors` is empty
- `agentCount > 0`

If validation fails, read `nextActions` and prefer `repair-config` or `setup-project`.

## Field Notes

`kaios.process-trace/v1` is the best source for process observability:

- `metrics.processCount`
- `metrics.tokenTotal`
- `metrics.contextBytes`
- `metrics.syscallCount`
- `path`
- `processes`
- `events`

`kaios.run-capsule/v1` is the best source for portable evidence:

- `run`
- `provenance.snapshotSha256`
- `provenance.traceSha256`
- `validation.valid`
- embedded `snapshot`
- embedded `trace`

`kaios.bug-report/v1` is the best source for support automation:

- `doctor.summary`
- `config.valid`
- `latestRun`
- `trace.valid`
- `nextActions`

`schemaVersion: 1` workspace analysis is intentionally separate from the `kaios.*` runtime schemas. Use it for dashboards and onboarding reports, not runtime correctness gates.
