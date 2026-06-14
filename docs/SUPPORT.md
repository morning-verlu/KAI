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

## Save A Report

```bash
kaios bug-report --out artifacts/kaios-bug-report.md --force
```

Use JSON when another tool needs to collect diagnostics:

```bash
kaios bug-report --json
kaios bug-report --format json --out artifacts/kaios-bug-report.json --force
```

JSON output uses schema `kaios.bug-report/v1`.
The report's next commands use the same onboarding path as the rest of the CLI: `kaios setup --ci` when no valid project workflow exists, or `kaios verify --config kaios.json` when one does.

## Better Reproduction

If there is no saved run yet, create a deterministic one:

```bash
kaios demo
kaios bug-report --out artifacts/kaios-bug-report.md --force
```

For project-specific issues, include the workflow, trace, and capsule check:

```bash
kaios verify
kaios capsule latest --check
kaios bug-report --out artifacts/kaios-bug-report.md --force
```
