# Project Setup

Use `kaios quickstart` when you want the shortest safe path from install to a validated project workflow and evidence capsule:

```bash
kaios quickstart
```

Use local-only quickstart when you want the same onboarding gate without writing a GitHub Actions workflow:

```bash
kaios quickstart --no-ci
```

Use `kaios setup` directly when you want to split onboarding into manual steps:

```bash
kaios setup --ci
```

This command:

- creates `kaios.json` from the `research` template when it is missing.
- keeps existing config files unless `--force` is passed.
- validates the workflow with `kaios.config-validation/v1`.
- runs readiness checks and warns about optional real-provider or persisted-memory env problems.
- writes `.github/workflows/kaios.yml` when `--ci` is passed and the project config is valid.
- points the generated Agent Gate at `kaios verify --config kaios.json --evidence --json --force`.
- uploads verify JSON, a portable capsule, and a failure-time bug report as `kaios-agent-gate`.
- prints `ci_artifact` and `ci_artifact_paths` so automation and maintainers know which GitHub Actions artifact to open.
- prints a workflow permission note because pushing `.github/workflows/kaios.yml` may require GitHub workflow scope.
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

For a copyable GitHub Actions Agent Gate matching the generated workflow, see [../examples/github-actions-agent-gate.yml](../examples/github-actions-agent-gate.yml).

Use a different template:

```bash
kaios setup --template code-review --ci
```

Repair an invalid or outdated generated config:

```bash
kaios setup --ci --force
```

If an existing config is invalid, `kaios setup --ci` reports `ci: skipped` and prints a `config validate` command plus the exact force command to repair the generated config. It will not write a CI gate for a workflow that cannot pass validation.

Use JSON for automation:

```bash
kaios setup --json
```

JSON output uses schema `kaios.setup/v1`.

## After Setup

Run the readiness gate:

```bash
kaios gate
kaios ps
kaios trace --check
```

`kaios gate` checks the local runtime, validates the project workflow, runs a deterministic mock smoke workflow, validates the process trace contract, saves a normal run snapshot, and confirms the same smoke run can become a portable audit package. Use `kaios verify --evidence-out artifacts/run.capsule.json` when you need a custom capsule path, or add `--baseline ... --check` when CI should compare against a known-good capsule.

Create a project artifact when the gate is ready:

```bash
kaios run --index . --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
```

If something behaves differently across machines:

```bash
kaios bug-report --out artifacts/kaios-bug-report.md --force
```
