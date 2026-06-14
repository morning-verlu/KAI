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

The generated `.github/workflows/kaios.yml` is intentionally no-key by default. It pins the current KAI OS CLI version, sets `KAIOS_MODEL_PROVIDER=mock`, and runs:

```bash
kaios verify --config kaios.json
```

This gives teams a stable gate for environment readiness, editable workflow validation, deterministic runtime execution, and process trace contract checks. The same command runs locally before pushing.

If the CI job should retain a portable run evidence package, add:

```bash
kaios capsule latest --check
kaios capsule latest --out artifacts/kaios-run.capsule.json --force
kaios capsule --file artifacts/kaios-run.capsule.json --check
```

## Repository CI

This source repository is ready for GitHub Actions. If workflow-file pushes are blocked, refresh the GitHub CLI session with the `workflow` scope:

```bash
gh auth refresh -h github.com -s workflow
```

Then add `.github/workflows/ci.yml`:

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
        uses: gradle/actions/setup-gradle@v6.2.0
        with:
          arguments: clean test installDist
```

After pushing the workflow, restore this README badge if desired:

```markdown
[![CI](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml/badge.svg)](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml)
```
