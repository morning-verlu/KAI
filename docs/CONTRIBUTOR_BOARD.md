# KAI OS Contributor Board

This board is for people who want to help KAI OS become a useful open-source agent runtime, but do not know where to start.

KAI OS is early. The highest-impact contributions are small, verifiable changes that make the Evidence OS loop easier to trust:

```text
Git change -> kaios review -> trace.json + capsule.json -> replay + CI gate
```

## Start In 5 Minutes

Use one of these paths before opening an issue or PR:

| Role | Action | Proof |
| --- | --- | --- |
| Browser evaluator | Open the [Evidence Viewer](https://morning-verlu.github.io/KAI/evidence-viewer.html) | Comment on what is clear or confusing |
| Kotlin/JVM developer | Read the [Kotlin Runtime API](KOTLIN_API.md) and run `./gradlew -p examples/kotlin-runtime-api run` | Open [Kotlin API feedback](https://github.com/morning-verlu/KAI/issues/new?template=kotlin_api_feedback.yml) |
| OSS maintainer | Read the [5-minute evaluator checklist](EVALUATE.md) | Say whether `kaios review` evidence would help PR review |
| CLI tester | Run `kaios tour` or `./scripts/codespaces-smoke.sh` | Paste the command result into an issue or PR |
| Docs contributor | Pick a docs-only starter issue | Open a small PR with `git diff --check` output |

## Current Starter Queues

These queues are maintained during launch:

- [Good first issues](https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22good%20first%20issue%22)
- [Help wanted issues](https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22help%20wanted%22)
- [Evidence issues](https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3Aevidence)
- [Feedback issues](https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3Afeedback)

If GitHub will not assign you yet, comment that you are taking the issue and open a PR from your fork. Repository write access is not required.

## Active Now

These are the best currently open tasks during launch:

| Issue | Best for | Good first move | Verification |
| --- | --- | --- | --- |
| [#14 Kotlin API capability recipe](https://github.com/morning-verlu/KAI/issues/14) | Kotlin/JVM docs contributor | Add one compact custom-tool/capability recipe to `docs/KOTLIN_API.md` | `git diff --check` and `./gradlew -p examples/kotlin-runtime-api run` |
| [#15 Denied-syscall walkthrough](https://github.com/morning-verlu/KAI/issues/15) | Evidence/docs contributor | Show where a denied syscall appears in checked-in trace or capsule output | `git diff --check` and `./scripts/evidence-samples-smoke.sh` |
| [#16 Docker smoke verification](https://github.com/morning-verlu/KAI/issues/16) | Docker/CI tester | Run `./scripts/docker-smoke.sh --preflight`, then full smoke on a normal network | `./scripts/docker-smoke.sh --preflight` and `./scripts/docker-smoke.sh` |
| [#12 Public repository CI](https://github.com/morning-verlu/KAI/issues/12) | Maintainer with GitHub auth access | Follow [CI_ENABLE_RUNBOOK.md](CI_ENABLE_RUNBOOK.md), refresh `workflow` scope, then copy the repository CI template into `.github/workflows/ci.yml` | `./scripts/repository-ci-smoke.sh` and GitHub Actions green run |

If you are unsure which one to pick, start with #14 or #15. They are docs-first and do not require write access to the repository.

## 30-Minute Contribution Ideas

Pick one narrow thing:

- Run the Codespaces path and note the slowest or least obvious step.
- Add one screenshot-free walkthrough for a checked-in evidence artifact.
- Add one Kotlin API recipe that uses a custom tool capability grant.
- Try `kaios review` on a small JVM project and report missing context.
- Improve one CLI output table so process evidence is easier to scan.
- Add one regression test around scheduler priority, recovery, or syscall denial.
- Run `./scripts/docker-smoke.sh --preflight`, then validate full Docker startup on a network that can download layers reliably.
- Share the [Evidence Viewer](https://morning-verlu.github.io/KAI/evidence-viewer.html) with one Kotlin/JVM developer and capture their first question.

## What Makes A Good PR

A good launch-stage PR is small and proves its effect.

Include:

- What changed.
- Why it helps the Evidence OS path.
- The narrowest verification command you ran.
- Any artifact, trace, or capsule path affected by the change.

Useful verification commands:

```bash
git diff --check
./scripts/codespaces-smoke.sh
./scripts/evidence-samples-smoke.sh
./scripts/repository-ci-smoke.sh
./gradlew test installDist
./gradlew -p examples/kotlin-runtime-api run
```

Do not run every command for every PR. Use the smallest command that proves the change.

## Help The Launch Without Writing Code

Code is not the only useful contribution.

- Star the repo if you want local-first agent evidence to exist in the JVM/Kotlin ecosystem.
- Watch releases if you want the Evidence OS loop to mature.
- Fork if you want to test KAI OS beside your own agent code.
- Share the [Evidence Viewer](https://morning-verlu.github.io/KAI/evidence-viewer.html) when someone asks what the project actually does.
- Open precise feedback instead of broad praise: API awkwardness, missing evidence, unclear install steps, or CI adoption blockers.

The project does not need artificial engagement. It needs real signals from people who care about auditable, replayable, CI-grade agent work.
