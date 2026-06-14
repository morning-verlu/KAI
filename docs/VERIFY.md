# Verify Gate

Use `kaios verify` when you want one answer to the question: is this KAI OS project ready to run and inspect?

```bash
kaios setup --ci
kaios verify
```

The command runs the same no-key checks locally and in CI:

- local runtime diagnostics from `kaios doctor`.
- project workflow validation from `kaios.config-validation/v1`.
- a deterministic `MockModelProvider` smoke workflow.
- process trace contract validation for `kaios.process-trace/v1`.
- optional evidence packaging through `--evidence`, with `--evidence-out` for custom paths.
- a normal run snapshot under `.kaios/runs/` for `ps`, `inspect`, `trace`, `capsule`, `evidence`, and `bug-report`.

`kaios verify` always runs the smoke workflow with the deterministic mock provider and session memory. If optional real-provider or persisted-memory environment variables are misconfigured, verify reports them as warnings instead of blocking the no-key gate. Use `kaios doctor` when you want those optional runtime settings to be checked strictly.

## Output

Text output is designed for humans:

```bash
kaios verify
```

JSON output is designed for CI, release gates, dashboards, and future Agent Desktop integrations:

```bash
kaios verify --json
```

The JSON schema is `kaios.verify/v1`. It keeps the existing `next` command list and adds structured `nextActions` with stable ids, commands, and reasons so CI, dashboards, and future Agent Desktop views do not need to parse shell text. See [JSON_CONTRACTS.md](JSON_CONTRACTS.md) for the full automation contract matrix.

When you want CI to retain a portable proof package from the same smoke run:

```bash
kaios verify --evidence --force
```

If your repository keeps a known-good baseline capsule:

```bash
kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
```

With `--evidence`, verify writes `artifacts/kaios-run.capsule.json`, validates it, replays it offline, and includes an `evidence` object in `kaios.verify/v1` JSON output. Use `--evidence-out <path>` when a custom capsule path is required.

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

`kaios setup --ci` writes `.github/workflows/kaios.yml` with the same gate and uploads the generated evidence capsule:

```bash
kaios verify --config kaios.json --evidence --force
```

That makes failures reproducible locally before pushing.
