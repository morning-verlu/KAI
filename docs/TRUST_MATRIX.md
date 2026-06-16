# KAI OS Trust Matrix

Use this page when you want to verify what KAI OS already does without reading the full source tree.

KAI OS is early, so the important trust question is not "is every future feature done?" The useful question is:

```text
Which Evidence OS claims are backed by checked-in artifacts or repeatable commands today?
```

## Status At A Glance

| Claim | Current proof | Command or artifact | Status |
| --- | --- | --- | --- |
| No API key is required for the first-run path | deterministic mock provider and local tour | `kaios tour` | Working |
| Agent runs produce process evidence | checked-in process trace and review JSON | `examples/evidence-sample/change-review.trace.json` | Working |
| Run capsules can be validated | checked-in capsule contract | `kaios capsule --file examples/evidence-sample/change-review.capsule.json --check` | Working |
| Capsules can replay offline | replay uses embedded snapshot, not a provider call | `kaios replay --file examples/evidence-sample/change-review.capsule.json` | Working |
| Baseline gates can fail on stable behavior drift | baseline/current capsules and expected diff | `./scripts/evidence-samples-smoke.sh` | Working |
| Kotlin/JVM developers can embed the runtime API | runnable Kotlin API example | `./gradlew -p examples/kotlin-runtime-api run` | Working |
| Repository trust path is repeatable locally | build, tests, samples, tour, replay | `./scripts/repository-ci-smoke.sh` | Working locally |
| Public GitHub Actions CI is ready to install | copyable workflow template | `examples/github-actions-repository-ci.yml` | Blocked by missing `workflow` token scope |
| External contribution path is open | first external PR merged and acknowledged | `CONTRIBUTORS.md` | Working |
| GitHub social preview is configured | custom OpenGraph image in repo settings | `docs/assets/kaios-social-card.png` | Working |

## Fast Verification

From a source checkout:

```bash
./scripts/evidence-samples-smoke.sh
```

This validates:

- the checked-in Evidence Sample capsule.
- offline replay for the Evidence Sample.
- baseline/current capsule contracts.
- offline replay for baseline/current capsules.
- stable normalized `kaios.run-diff/v1` output.
- `kaios diff --check` exiting `1` for valid changed runtime behavior.

For the full repository trust path:

```bash
./scripts/repository-ci-smoke.sh
```

This also runs Gradle tests, installs the CLI, runs the Kotlin runtime API example, generates a baseline diff sample, runs `kaios tour`, validates the generated tour capsule, and replays it offline.

## Checked-In Evidence

| Artifact | What to inspect |
| --- | --- |
| `examples/evidence-sample/change-review.md` | human-readable review artifact |
| `examples/evidence-sample/change-review.trace.json` | process table, events, scheduler, syscalls, and cost evidence |
| `examples/evidence-sample/change-review.capsule.json` | portable run capsule with snapshot and trace |
| `examples/evidence-sample/review-result.json` | stable `kaios.review/v1` CLI/CI contract |
| `examples/baseline-gate/capsules/baseline.capsule.json` | known-good runtime behavior |
| `examples/baseline-gate/capsules/current-different.capsule.json` | changed runtime behavior |
| `examples/baseline-gate/expected/diff.stable.json` | normalized stable diff expected by smoke tests |
| `examples/kotlin-runtime-api` | embeddable Kotlin/JVM runtime API example |

## Honest Gaps

- Public GitHub Actions CI is not committed yet because the current GitHub token does not include `workflow` scope. The workflow template is ready in `examples/github-actions-repository-ci.yml`.
- Docker is documented, but full Docker smoke has been slow in this environment because of external image/package downloads. Track it in issue #16.
- Real model providers are intentionally outside the default trust path. The first-run path stays deterministic and local.
- Public GitHub Actions CI and Docker smoke are the remaining infrastructure trust gaps; the GitHub Social Preview image is uploaded and verified.

## Why This Matters

The Evidence OS pitch is only interesting if it can be checked. This matrix keeps the project honest: every major claim should point to a file, command, artifact, issue, or explicit gap.

Next links:

- [Start here](../START_HERE.md)
- [Evaluate in 5 minutes](EVALUATE.md)
- [Why star KAI OS](WHY_STAR.md)
- [Trust contract](TRUST.md)
- [JSON contracts](JSON_CONTRACTS.md)
