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
| `kaios tour --json` | `kaios.tour/v1` | Disposable first-run Evidence OS tour with generated review, trace, capsule, and next actions. |
| `kaios next --json` | `kaios.next/v1` | Read-only workspace compass with one prioritized command. |
| `kaios quickstart --json` | `kaios.quickstart/v1` | One-command onboarding state across demo, setup, verify, and evidence. |
| `kaios review --json` | `kaios.review/v1` | Current-change review artifact, process trace, capsule, replay proof, optional baseline diff, and next actions. |
| `kaios setup --json` | `kaios.setup/v1` | Bootstrap state, generated files, validation, and next actions. |
| `kaios verify --json` | `kaios.verify/v1` | One-command local and CI readiness gate. |
| `kaios config validate --json` | `kaios.config-validation/v1` | Workflow config validation without starting agents. |
| `kaios doctor --json` | `kaios.doctor/v1` | Machine-readable environment diagnostics. |
| `kaios doctor --fix --json` | `kaios.doctor-fix/v1` | Preview or apply the local repair path for project workflow files. |
| `kaios bug-report --json` | `kaios.bug-report/v1` | Safe support bundle for issues and handoff. |
| `kaios runs --json` | `kaios.runs/v1` | Saved run registry for local tooling and UI lists. |
| `kaios ps --json` | `kaios.process-table/v1` | Process table with recovery, scheduler, and cost summaries. |
| `kaios trace <run> --json` | `kaios.process-trace/v1` | Process metrics, path, and lifecycle timeline. |
| `kaios capsule <run> --json` | `kaios.run-capsule/v1` | Portable run package with snapshot, trace, and provenance. |
| `kaios replay --file capsule.json --json` | `kaios.run-replay/v1` | Offline capsule replay and deterministic trace rebuild checks. |
| `kaios diff left.capsule.json right.capsule.json --json` | `kaios.run-diff/v1` | Stable run comparison for regression gates. |
| `kaios evidence <run> --json` | `kaios.evidence/v1` | Capsule packaging, validation, replay, optional diff, and next actions. |
| `kaios recover <run> --dry-run --json` | `kaios.recover/v1` | Dry-run recovery report for crashed agent processes. |
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
- `kaios.next/v1`
- `kaios.quickstart/v1`
- `kaios.review/v1`
- `kaios.config-validation/v1`
- `kaios.doctor/v1`
- `kaios.doctor-fix/v1`
- `kaios.bug-report/v1`
- `kaios.evidence/v1`
- `kaios.tour/v1`

`next` is a list of shell commands for people and existing scripts:

```json
{
  "next": [
    "kaios ps",
    "kaios evidence",
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
      "command": "kaios evidence",
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
| `repair-project` | Preview or apply the safest local repair for failed diagnostics. |
| `stage-generated-files` | Stage generated config and CI files. |
| `quickstart` | Run the no-key onboarding gate and create inspectable evidence. |
| `review-current-change` | Review the current Git change set and package replayable evidence. |
| `setup-project` | Create a validated workflow and optional CI gate. |
| `regenerate-config` | Regenerate an invalid workflow config with an executable `kaios setup ... --force` command. |
| `validate-config` | Validate workflow config without running agents. |
| `show-config` | Inspect agents, tools, dependencies, and fallback routes. |
| `verify-project` | Run the readiness gate and optionally write evidence. |
| `run-demo` | Create a no-key run snapshot for inspection and support. |
| `create-project-artifact` | Turn the current workspace into a saved run, trace, and Markdown handoff artifact. |
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
| `run-diagnostics` | Run local diagnostics. |
| `next` | Generic fallback for uncategorized commands. |

## Recommended Gates

### Workspace Next Gate

Use `kaios.next/v1` when docs, bots, issue templates, or a future UI need one safe next command without writing files:

```bash
kaios next --json
```

Read:

- `status`: `repair`, `review`, `verify`, `inspect`, or `ready`.
- `action`: the single command to show first.
- `fixFirst`: the first repair or verification action, or `null` when the workspace is already inspectable.
- `signals`: compact doctor, config, latest run, and trace state.
- `nextActions`: the full ordered action list for automation.

The priority order is intentionally product-facing:

- repair invalid configs or failed diagnostics first.
- review current Git changes before gates when the working tree is dirty.
- verify with `kaios gate --config ...` when config is valid but no run evidence exists.
- create a bounded project artifact after onboarding-only evidence is healthy.
- inspect with `kaios ps` once a real task run is already healthy.

### Onboarding Gate

Use `kaios.quickstart/v1` when automation or docs need to prove the first-run path works:

```bash
kaios quickstart --json
```

Gate on:

- `status == "ready"`
- `plan.writes` matches the files your onboarding flow is allowed to create or keep.
- `demo.success == true`
- `setup.validation.valid == true`
- `verify.status == "ready"`
- `verify.evidence.valid == true`
- `errors` is empty

Use `kaios quickstart --dry-run --json` when automation or docs need to preview the first-run write plan without creating `kaios.json`, `.github/workflows/kaios.yml`, snapshots, traces, or evidence. In that mode `status == "planned"`, `plan.dryRun == true`, and `demo`, `setup`, and `verify` are `null`.
Use `kaios quickstart --no-ci --json` when automation should prove local onboarding without writing `.github/workflows/kaios.yml`. In that mode `setup.ci.action == "skipped"` and `setup.ciArtifact == null`.
Read `nextActions` to send the user to process inspection, trace validation, or the first project run without parsing terminal text.

### Review Gate

Use `kaios.review/v1` when a PR bot, CI job, or local script needs one current-change review path:

```bash
kaios review --json
kaios review --baseline artifacts/baseline.capsule.json --check --json
```

Gate on:

- `status == "valid"` when no baseline is required.
- `replay.valid == true`.
- `baselineDiff.status == "same"` when `--baseline ... --check` is required to match.
- `changedFiles.total > 0`.
- `artifact.exists == true`, `trace.exists == true`, and `capsule.exists == true`.

Read:

- `changedFiles.files` for the changed-file list and which paths were attached as bounded context.
- `artifact.path` for the Markdown review handoff.
- `trace.path` for the `kaios.process-trace/v1` file.
- `capsule.path` for the portable replayable evidence capsule.
- `nextActions` for process inspection, trace checks, replay, or baseline comparison.

When the Git workspace is clean, `kaios review` exits `1` and prints executable next commands instead of creating empty evidence.
When running with `--check` and a baseline, KAI OS exits `1` for valid-but-different behavior and `2` for invalid evidence.

### Bootstrap Gate

Use `kaios.setup/v1` when automation needs to know what setup wrote:

```bash
kaios setup --ci --json
```

Read:

- `config.path` and `config.action` for the project workflow file.
- `ci.path` and `ci.action` for the generated GitHub Actions Agent Gate.
- `ciArtifact.name` and `ciArtifact.paths` for the uploaded `kaios-agent-gate` bundle when CI is enabled.
- `ciArtifact.pushPermissionNote` before asking users to push generated GitHub Actions workflows.
- `validation.valid == true` before committing the generated workflow.

### Doctor Repair Gate

Use `kaios.doctor-fix/v1` when automation needs to preview or apply the repair path suggested by diagnostics:

```bash
kaios doctor --fix --dry-run --json
kaios doctor --fix --json
```

Gate on:

- `status == "planned"` for dry-run previews.
- `status == "fixed"` for applied repairs.
- `plan.writes` matches the config and optional CI files your automation is allowed to create or keep.
- `setup.validation.valid == true` after an applied repair.
- `after.summary.status == "ready"` after an applied repair.
- `errors` is empty after an applied repair.

Use `--ci` when the repair should also write `.github/workflows/kaios.yml`.
Use `--force` only after surfacing the dry-run plan to a person, because existing config and CI files are kept by default.

### Readiness Gate

Use `kaios.verify/v1` when CI needs one answer:

```bash
kaios setup --ci
kaios verify --json
```

Gate on:

- `status == "ready"`
- `diagnosis.status == "ready"`
- `config.valid == true`
- `run.success == true`
- `trace.valid == true`
- `errors` is empty

Read `diagnosis` before drilling into nested artifacts. It gives people, CI bots, and future Agent Desktop views the same Agent Gate summary:

- `diagnosis.status`: `ready`, `failed`, or `different`.
- `diagnosis.verdict`: one short user-facing sentence.
- `diagnosis.reasons`: stable failure or drift reasons suitable for CI annotations.
- `diagnosis.fixFirst`: the first `nextActions`-style command to run, or `null`. For project config failures this is a `repair-project` dry-run command so users can preview writes before applying them.
- `diagnosis.diffChanges`: the first stable baseline differences when `--baseline ... --check` finds drift.

### Evidence Gate

Use `kaios.evidence/v1` when CI needs a portable proof artifact:

```bash
kaios verify --evidence --json --force
kaios evidence --summary
```

Gate on:

- `status == "valid"`
- `valid == true`
- `capsule.valid == true`
- `replay.valid == true`
- `diff.valid == true`
- `diff.status == "same"` when a baseline is required to match

When running with `--check` and a baseline, KAI OS exits `1` for a valid-but-different result and `2` for invalid evidence.
Use `kaios evidence --summary` when a PR or CI surface needs short Markdown with Verdict, Changed Runtime Behavior, Fix First, and Process Table sections.

### Config Gate

Use `kaios.config-validation/v1` when a job only needs to check workflow files:

```bash
kaios config validate --config workflows/research.json --json
```

Gate on:

- `valid == true`
- `errors` is empty
- `agentCount > 0`

If validation fails, read `nextActions` and prefer `repair-project` when present. Existing invalid configs should surface the dry-run repair command before `regenerate-config`; missing configs should surface the dry-run repair command before `setup-project`. `repair-config` is retained for older human-readable repair notes.

### Support Gate

Use `kaios.bug-report/v1` when a job, issue template, or support workflow needs one safe diagnostic payload:

```bash
kaios bug-report --json
```

Read:

- `fixFirst` before scanning `next` when it is not `null`.
- `doctor.summary` for environment readiness.
- `config.valid` and `config.errors` for workflow repair decisions.
- `latestRun` and `trace` when a saved run exists.
- `nextActions` for the complete action list.

For missing or invalid project configs, `fixFirst.id == "repair-project"` and the command is the dry-run repair preview.
For a valid project config without a saved run, `fixFirst.id == "verify-project"` so the project gate creates inspectable evidence before deeper debugging.
Use `kaios.next/v1` when the support surface only needs the first command instead of the full report body.

## Field Notes

`kaios.process-trace/v1` is the best source for process observability:

- `metrics.processCount`
- `metrics.tokenTotal`
- `metrics.contextBytes`
- `metrics.syscallCount`
- `path`
- `processes`
- `events`
- `scheduler`
- `syscalls`
- `cost`
- `recovery`

v0.3 adds Evidence Core fields without changing existing field meanings:

- `scheduler`: executor backend, priority usage, trigger count, and recovery policy usage.
- `syscalls`: audit records with call id, PID, agent, tool, permission, allowed/denied, duration, estimated cost, redacted arguments, and error.
- `cost`: token total, tool time, estimated cost micros, and denied syscall count.
- `recovery`: recovered process count, crash count, and recoverable process count.

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

`kaios.next/v1` is the best source for a first-class "what should I do now?" UI:

- `status`
- `action`
- `fixFirst`
- `signals`
- `nextActions`

When Git metadata is available, `signals` can include `changes` with `clean` or `dirty` status. A dirty working tree changes `status` to `review` and makes `action` / `fixFirst` recommend `kaios review` before readiness gates or evidence packaging.

`schemaVersion: 1` workspace analysis is intentionally separate from the `kaios.*` runtime schemas. Use it for dashboards and onboarding reports, not runtime correctness gates. Its `changeSummary` object reports whether Git metadata was available, whether the working tree is dirty, changed and untracked file counts, and a capped changed-file list. Its `actionPlan` array contains prioritized actions with stable `id`, `priority`, `action`, `command`, and `reason` fields so tools can surface a useful next move without parsing Markdown tables.
