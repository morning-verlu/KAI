# CI Setup

KAI OS has two CI paths:

- downstream projects can generate an Agent Gate with the CLI.
- this repository can add a Gradle build workflow when the GitHub token has `workflow` scope.

## Agent Gate

In a project that uses KAI OS, run:

```bash
kaios setup --ci
git add kaios.json .github/workflows/kaios.yml
```

The generated `.github/workflows/kaios.yml` is intentionally no-key by default. It pins the current KAI OS CLI version, sets `KAIOS_MODEL_PROVIDER=mock`, runs the Agent Gate as JSON, appends a Markdown step summary, and uploads a small audit bundle:

```bash
mkdir -p artifacts
kaios gate --config kaios.json --summary-out "$GITHUB_STEP_SUMMARY" --json | tee artifacts/kaios-verify.json
```

The uploaded `kaios-agent-gate` artifact includes `kaios-verify.json`, `kaios-run.capsule.json`, and, when the gate fails, `kaios-bug-report.json`. The step summary shows readiness, process metrics, trace status, and capsule path directly in the GitHub Actions UI. Failed summaries put Why It Failed and Fix First above the metrics table, with project config failures pointing to `kaios doctor --fix --dry-run ...` before any write command. Baseline failures include a What Changed table with stable runtime differences. The verify JSON mirrors that decision under `diagnosis.status`, `diagnosis.reasons`, `diagnosis.fixFirst`, and `diagnosis.diffChanges`, so bots and dashboards do not need to parse Markdown. This keeps common config and regression issues actionable from the PR page. The same `kaios gate --config kaios.json` command runs locally before pushing.

For a copyable workflow file matching the generated gate, see [../examples/github-actions-agent-gate.yml](../examples/github-actions-agent-gate.yml). The CLI smoke tests compare that file with the generated default workflow so documentation drift is caught during development.

If the repository keeps a known-good baseline capsule, add a regression diff gate:

```bash
kaios gate --config kaios.json --baseline artifacts/baseline.capsule.json --check
```

## Repository CI

This source repository is ready for GitHub Actions. If workflow-file pushes are blocked, refresh the GitHub CLI session with the `workflow` scope:

```bash
gh auth refresh -h github.com -s workflow
```

For the exact maintainer procedure, verification commands, and rollback path,
see [CI_ENABLE_RUNBOOK.md](CI_ENABLE_RUNBOOK.md).

Until that scope is available, the repository CI path can still be verified locally:

```bash
./scripts/repository-ci-smoke.sh
```

That script runs the full no-key trust path intended for public CI:

- `./gradlew clean test installDist --no-daemon`
- CLI version check
- checked-in Evidence Sample and Baseline Gate replay checks
- Kotlin runtime API example execution
- no-key `kaios tour`
- generated tour capsule validation
- generated tour capsule offline replay

For a copyable workflow template, see [../examples/github-actions-repository-ci.yml](../examples/github-actions-repository-ci.yml). Once `workflow` scope is available, copy it to `.github/workflows/ci.yml`.

The workflow body is:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v6.0.3

      - name: Set up Java
        uses: actions/setup-java@v5.2.0
        with:
          distribution: temurin
          java-version: "17"

      - name: Validate Gradle wrapper
        uses: gradle/actions/wrapper-validation@v6.2.0

      - name: Build and test
        run: ./scripts/repository-ci-smoke.sh
```

After pushing the workflow, restore this README badge if desired:

```markdown
[![CI](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml/badge.svg)](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml)
```
