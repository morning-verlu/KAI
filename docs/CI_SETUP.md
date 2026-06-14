# CI Setup

The repository is ready for GitHub Actions, but adding workflow files through the current automation token was blocked because the token did not include the `workflow` scope.

## Fix

Run this locally with a GitHub CLI session that can request `workflow` scope:

```bash
gh auth refresh -h github.com -s workflow
```

Then add this file as `.github/workflows/ci.yml`:

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

After installing KAI OS in downstream projects, `kaios doctor --json` can be used as a machine-readable readiness check. It emits `kaios.doctor/v1` with a summary, check list, and safe next commands without printing API secrets.

After pushing the workflow, restore this README badge if desired:

```markdown
[![CI](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml/badge.svg)](https://github.com/morning-verlu/KAI/actions/workflows/ci.yml)
```
