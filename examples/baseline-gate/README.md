# Baseline Gate Evidence Sample

This sample shows how KAI OS turns two valid run capsules into a CI-style baseline gate.

It uses the same tiny billing-service scenario as [JVM Service Review](../jvm-service-review/):

- `baseline.capsule.json`: the known-good run before the payment-plan change.
- `current-different.capsule.json`: a later run after the payment-plan change.
- `expected/diff.stable.json`: a normalized, path-light view of the stable runtime differences.
- `expected/evidence-summary.md`: the PR/CI-style Markdown shape readers should expect.

The current capsule is checked in only so this example can be replayed offline. In a real project, commit the baseline capsule and let CI generate the current capsule.

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
