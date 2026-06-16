# Verify Gate

Use `kaios gate` when you want one answer to the question: is this KAI OS project ready to run, inspect, and audit?

```bash
kaios setup --ci
kaios gate
```

`kaios gate` is the product shortcut for `kaios verify --evidence --force`. It runs the same no-key checks locally and writes the same portable evidence package used by CI:

- local runtime diagnostics from `kaios doctor`.
- project workflow validation from `kaios.config-validation/v1`.
- a deterministic `MockModelProvider` smoke workflow.
- process trace contract validation for `kaios.process-trace/v1`.
- evidence packaging to `artifacts/kaios-run.capsule.json`.
- a normal run snapshot under `.kaios/runs/` for `ps`, `inspect`, `trace`, `capsule`, `evidence`, and `bug-report`.

`kaios verify` remains the lower-level command when you need protected evidence outputs or a custom `--evidence-out` path. Both commands always run the smoke workflow with the deterministic mock provider and session memory. If optional real-provider or persisted-memory environment variables are misconfigured, the gate reports them as warnings instead of blocking the no-key path. Use `kaios doctor` when you want those optional runtime settings to be checked strictly.

## Output

Text output is designed for humans:

```bash
kaios gate
```

JSON output is designed for CI, release gates, dashboards, and future Agent Desktop integrations:

```bash
kaios gate --json
```

The JSON schema is `kaios.verify/v1`. It keeps the existing `next` command list and adds structured `nextActions` with stable ids, commands, and reasons so CI, dashboards, and future Agent Desktop views do not need to parse shell text. See [JSON_CONTRACTS.md](JSON_CONTRACTS.md) for the full automation contract matrix.

The lower-level equivalent is:

```bash
kaios verify --evidence --force
```

To validate the checked-in Evidence Sample and Baseline Gate artifacts without a model provider:

```bash
./scripts/evidence-samples-smoke.sh
```

To validate the browser-only Codespaces path from a fresh checkout:

```bash
./scripts/codespaces-smoke.sh
```

To validate the Docker path:

```bash
./scripts/docker-smoke.sh
```

When the image is already built, rerun the same smoke without another Docker build:

```bash
./scripts/docker-smoke.sh --no-build
```

To run the same no-key checks intended for this repository's future public CI:

```bash
./scripts/repository-ci-smoke.sh
```

When a GitHub Actions workflow wants a human-readable PR summary and a clean JSON artifact at the same time:

```bash
kaios gate --summary-out "$GITHUB_STEP_SUMMARY" --json | tee artifacts/kaios-verify.json
```

`--summary-out` appends Markdown, so it works with GitHub Step Summary files and local release-note artifacts without changing the `kaios.verify/v1` JSON printed to stdout. On failure, the summary starts with Verdict, Why It Failed, and Fix First sections so a maintainer can act before opening the JSON artifact. For project config failures, Fix First points to `kaios doctor --fix --dry-run ...` before any command that writes files. The same decision is exposed in JSON under `diagnosis.status`, `diagnosis.reasons`, `diagnosis.fixFirst`, and `diagnosis.diffChanges` for CI annotations and future UI surfaces. When `--baseline ... --check` finds behavior changes, the summary also includes a What Changed table with the first stable runtime differences.

If your repository keeps a known-good baseline capsule:

```bash
kaios gate --baseline artifacts/baseline.capsule.json --check
```

The gate writes `artifacts/kaios-run.capsule.json`, validates it, replays it offline, and includes an `evidence` object in `kaios.verify/v1` JSON output. Use `kaios verify --evidence-out <path>` when a custom capsule path is required.

## Config Path

By default, verify reads `kaios.json` from the current directory:

```bash
kaios verify
```

Use `--config` for another workflow:

```bash
kaios verify --config workflows/research.json
kaios doctor --config workflows/research.json
kaios bug-report --config workflows/research.json
```

The same `--config` path is honored by `doctor` and `bug-report`, so support diagnostics do not fall back to the default `kaios.json` in repositories with multiple workflow files.

## Exit Codes

- `0`: doctor, config validation, smoke run, and trace contract all passed.
- `1`: command usage was invalid, or `--check` found a valid baseline evidence difference.
- `2`: the readiness gate or evidence contract failed.

## CI

`kaios setup --ci` writes `.github/workflows/kaios.yml` with the same no-key gate and uploads a small audit bundle:

```bash
mkdir -p artifacts
kaios gate --config kaios.json --summary-out "$GITHUB_STEP_SUMMARY" --json | tee artifacts/kaios-verify.json
```

The uploaded `kaios-agent-gate` artifact includes the verify JSON, the generated evidence capsule, and a failure-time JSON bug report. The workflow also appends a Markdown summary to the GitHub Actions run, which makes the agent process metrics visible before anyone downloads artifacts.
