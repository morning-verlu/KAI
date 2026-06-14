# Project Setup

Use `kaios setup` when you want the shortest safe path from install to a validated project workflow:

```bash
kaios setup --ci
```

This command:

- creates `kaios.json` from the `research` template when it is missing.
- keeps existing config files unless `--force` is passed.
- validates the workflow with `kaios.config-validation/v1`.
- runs readiness checks and warns about optional real-provider or persisted-memory env problems.
- writes `.github/workflows/kaios.yml` when `--ci` is passed.
- points the generated Agent Gate at `kaios verify --config kaios.json`.
- prints the next useful commands.

## Common Paths

Create a local workflow only:

```bash
kaios setup
```

Create a workflow plus the GitHub Actions Agent Gate:

```bash
kaios setup --ci
git add kaios.json .github/workflows/kaios.yml
```

Use a different template:

```bash
kaios setup --template code-review --ci
```

Repair an invalid or outdated generated config:

```bash
kaios setup --force
```

Use JSON for automation:

```bash
kaios setup --json
```

JSON output uses schema `kaios.setup/v1`.

## After Setup

Run the readiness gate:

```bash
kaios verify
kaios ps latest
kaios trace latest --check
kaios capsule latest --check
```

`kaios verify` checks the local runtime, validates the project workflow, runs a deterministic mock smoke workflow, validates the process trace contract, and saves a normal run snapshot. `kaios capsule latest --check` confirms the saved run can become a portable audit package.

Create a project artifact when the gate is ready:

```bash
kaios run --index . --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
```

If something behaves differently across machines:

```bash
kaios bug-report --out artifacts/kaios-bug-report.md --force
```
