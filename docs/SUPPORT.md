# Support Reports

When KAI OS behaves differently across machines, start with a safe support report:

```bash
kaios bug-report
```

The default output is Markdown you can paste into a GitHub issue. It includes:

- CLI version and working directory.
- `kaios doctor` checks for Java, writable runtime directories, provider config, memory, HTTP allowlist, project config, and run snapshots.
- `kaios.config-validation/v1` status for `kaios.json`.
- latest run summary when a saved run exists.
- latest process trace contract status from `kaios.process-trace/v1`.
- next command for `kaios.run-capsule/v1` evidence packaging when a saved run exists.
- safe next commands.

It does not print API keys, tokens, or secret environment values. Do not manually add secrets when pasting the report into an issue.
When your workflow config is not `kaios.json`, pass the same path to support commands so diagnostics and next commands follow the right file:

```bash
kaios doctor
kaios doctor --fix --dry-run
kaios doctor --fix
kaios doctor --config workflows/research.json --json
kaios bug-report --config workflows/research.json
```

## Save A Report

```bash
kaios bug-report --out artifacts/kaios-bug-report.md --force
kaios bug-report --config workflows/research.json --out artifacts/kaios-bug-report.md --force
```

Use JSON when another tool needs to collect diagnostics:

```bash
kaios bug-report --json
kaios bug-report --config workflows/research.json --json
kaios bug-report --format json --out artifacts/kaios-bug-report.json --force
```

JSON output uses schema `kaios.bug-report/v1`.
The report's `next` commands and structured `nextActions` use the same onboarding path as the rest of the CLI:

For the full JSON command matrix and shared action ids, see [JSON_CONTRACTS.md](JSON_CONTRACTS.md).

- missing project config: `kaios doctor --fix --dry-run`, then `kaios doctor --fix` or `kaios doctor --fix --ci`.
- valid project config: `kaios gate --config kaios.json`.
- existing but invalid project config: `kaios config validate --config kaios.json --json`, then rerun the executable repair command `kaios doctor --fix --dry-run --force` followed by `kaios doctor --fix --force`, or edit the file manually.

`kaios doctor --fix` reuses the same setup contract as `kaios setup`; existing config and CI files are kept unless `--force` is passed.

## Better Reproduction

If there is no saved run yet, create a deterministic one:

```bash
kaios demo
kaios bug-report --out artifacts/kaios-bug-report.md --force
```

For project-specific issues, include the workflow, trace, and capsule evidence:

```bash
kaios gate
kaios bug-report --out artifacts/kaios-bug-report.md --force
kaios gate --config workflows/research.json
kaios bug-report --config workflows/research.json --out artifacts/kaios-bug-report.md --force
```
