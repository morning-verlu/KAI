# Baseline Gate Evidence Sample

This sample shows how `kaios diff --check` catches changes in agent runtime behavior — and why that exit code `1` is a feature, not a bug.

## What Is A Baseline Gate?

A baseline gate answers one question: **did the agent's runtime behavior change since last time?**

Here is how it works, step by step:

1. You run your agent workflow and save the output as a **baseline capsule**. This is your known-good snapshot — the run you reviewed and were happy with.
2. Later, something in your project changes. Maybe a source file was updated, maybe a new test was added. You run the workflow again and get a **current capsule**.
3. `kaios diff` compares the two. It does not care about timestamps, run ids, or how long each step took. It looks at what actually matters: which processes ran, how many tokens they used, what syscalls happened, and whether the final output changed.
4. If the two capsules match on those stable fields, `kaios diff --check` exits `0`. Nothing interesting happened.
5. If something changed, it exits `1`. That is on purpose — it means "hey, the agent behaved differently this time, someone should take a look."

The important thing to understand: **exit `1` is not a test failure.** Both capsules are perfectly valid. Both replay fine. The gate is just telling you that stable runtime behavior shifted, and you should review the diff before merging.

## What Is In This Folder

This sample uses a small billing-service scenario (same one as [JVM Service Review](../jvm-service-review/)):

| File | What it is |
| --- | --- |
| `capsules/baseline.capsule.json` | The known-good run, captured before a payment-plan code change |
| `capsules/current-different.capsule.json` | A later run after the code change — valid, but different |
| `expected/diff.stable.json` | The normalized diff output showing what actually changed |
| `expected/evidence-summary.md` | The PR-friendly Markdown summary a reviewer would see |
| `ci/github-actions-baseline-gate.yml` | A ready-to-copy GitHub Actions workflow for your own repo |

The current capsule is checked in so you can try this offline. In a real project, you would only commit the baseline and let CI generate the current capsule on each push.

## Shortest Way To See It

If you just want to see the diff without reading any JSON, run this one command after building KAI OS:

```bash
./gradlew installDist

build/install/kaios-cli/bin/kaios diff \
  examples/baseline-gate/capsules/baseline.capsule.json \
  examples/baseline-gate/capsules/current-different.capsule.json \
  --check
```

It will exit `1` and print the differences. That is the whole point — the two runs are both valid, but the runtime behavior changed because the billing service code changed between them.

## Try The Gate

From the KAI OS repository:

```bash
./gradlew installDist

KAIOS_BIN="$PWD/build/install/kaios-cli/bin/kaios"

"$KAIOS_BIN" capsule --file examples/baseline-gate/capsules/baseline.capsule.json --check
"$KAIOS_BIN" capsule --file examples/baseline-gate/capsules/current-different.capsule.json --check

"$KAIOS_BIN" replay --file examples/baseline-gate/capsules/baseline.capsule.json
"$KAIOS_BIN" replay --file examples/baseline-gate/capsules/current-different.capsule.json

"$KAIOS_BIN" diff \
  examples/baseline-gate/capsules/baseline.capsule.json \
  examples/baseline-gate/capsules/current-different.capsule.json \
  --check
```

The final command should exit `1`. That is the point: both capsules are valid and replayable, but stable runtime behavior changed, so a CI gate would block the change.

Use JSON when automation needs stable fields:

```bash
"$KAIOS_BIN" diff \
  examples/baseline-gate/capsules/baseline.capsule.json \
  examples/baseline-gate/capsules/current-different.capsule.json \
  --json |
  jq '{schema,result,valid,same,checks,metricsDelta,fields:[.differences[].field]}'
```

Expected stable fields:

```text
result: different
same: false
checks.leftCapsule: true
checks.rightCapsule: true
checks.leftReplay: true
checks.rightReplay: true
fields: task, finalOutputSha256, metrics.tokenTotal, metrics.inputTokens, metrics.contextBytes, processes
```

## What The Gate Ignores

Do not compare raw capsule JSON with `diff -u`.

Raw capsules include run ids, timestamps, paths, duration noise, and provenance hashes. `kaios diff` validates and replays both capsules first, then compares the stable runtime signature: workflow, task, success, final output hash, process path, process states, tokens, context, syscalls, and event counts.

## CI Pattern

See [ci/github-actions-baseline-gate.yml](ci/github-actions-baseline-gate.yml) for a copyable shape.

In a real repository:

- Commit a reviewed baseline capsule under a path such as `baselines/kaios-run.capsule.json`.
- Run `kaios gate --baseline baselines/kaios-run.capsule.json --check` in CI.
- Let CI upload the generated current capsule and JSON evidence as artifacts.
- Treat exit `1` as a valid evidence difference that should block until reviewed.

Before committing any baseline capsule, inspect it for secrets. KAI OS respects `.kaiosignore`, but a capsule is still an artifact produced from local run state.

## Changed Behavior Is Not A Bug

If you are coming from a normal test suite, exit `1` probably feels wrong. In most tools, `1` means something broke.

In a baseline gate, it means something *changed*. The old run and the new run are both valid. They both replay. They just disagree on things like token counts, context size, or final output — and that is exactly the kind of drift you want to catch before it reaches production.

When you see exit `1` from `kaios diff --check`, read the diff output. If the changes make sense (you added a file, you changed a test, you updated a dependency), update the baseline. If they do not make sense, you just caught a regression that a normal test would have missed.
