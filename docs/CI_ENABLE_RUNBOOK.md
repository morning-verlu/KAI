# Public CI Enablement Runbook

This runbook turns issue #12 into a short maintainer task. It exists because
GitHub blocks pushes to `.github/workflows/*` unless the active token includes
the `workflow` scope.

## Current Blocker

The active GitHub CLI token currently has:

```text
gist, read:org, repo
```

It does not currently include:

```text
workflow
```

Do not push `.github/workflows/ci.yml` until the maintainer refreshes auth with
the workflow scope.

## Maintainer Steps

Refresh the GitHub CLI auth scope:

```bash
gh auth refresh -h github.com -s workflow
gh auth status
```

`gh auth status` should show `workflow` in the token scopes before continuing.

Create the workflow from the checked-in template:

```bash
mkdir -p .github/workflows
cp examples/github-actions-repository-ci.yml .github/workflows/ci.yml
```

Verify the exact no-key CI path locally:

```bash
git diff --check
./scripts/repository-ci-smoke.sh
```

Commit and push:

```bash
git add .github/workflows/ci.yml docs/CI_ENABLE_RUNBOOK.md docs/CI_SETUP.md docs/LAUNCH.md docs/CONTRIBUTOR_BOARD.md
git commit -m "Enable repository CI"
git push origin HEAD:main
```

## Verify On GitHub

After pushing, check that the workflow appears and completes:

```bash
gh run list --repo morning-verlu/KAI --workflow ci.yml --limit 5
```

The workflow should run `./scripts/repository-ci-smoke.sh`, which covers:

- Gradle clean build, tests, and install distribution.
- CLI version check.
- checked-in Evidence Sample and Baseline Gate validation.
- Kotlin runtime API example execution.
- no-key `kaios tour`.
- generated capsule validation and offline replay.

No API key is required. The workflow uses Java 17 and the deterministic mock
provider path.

## Rollback

If the workflow fails because of GitHub Actions environment drift, revert the
workflow commit and keep the local smoke path as the temporary trust signal:

```bash
git revert <workflow-commit-sha>
git push origin HEAD:main
```

Then update issue #12 with the failing run URL, the error summary, and whether
`./scripts/repository-ci-smoke.sh` still passes locally.

## Issue Update Template

Use this when posting back to issue #12:

```markdown
Public repository CI has been enabled.

- Workflow: `.github/workflows/ci.yml`
- Template source: `examples/github-actions-repository-ci.yml`
- Local verification: `./scripts/repository-ci-smoke.sh`
- GitHub Actions run: <run-url>

This remains a no-key Evidence OS trust path: build/test/install, checked-in
capsule validation, baseline gate replay, Kotlin runtime API example, tour
capsule validation, and offline replay.
```
