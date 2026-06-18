# Evidence Sample

This folder is a checked-in sample of the `kaios review` product path. It lets a visitor inspect KAI OS evidence artifacts before installing the CLI.

The sample was generated from a disposable Git repository with one Kotlin file change:

```text
src/main/kotlin/App.kt  M
```

Run path:

```bash
kaios review --json
```

Generated artifacts:

- [change-review.md](change-review.md): the human-readable review artifact with process table and lifecycle events.
- [change-review.trace.json](change-review.trace.json): `kaios.process-trace/v1` with process metrics, scheduler evidence, syscalls, costs, and event timeline.
- [change-review.capsule.json](change-review.capsule.json): `kaios.run-capsule/v1`, a portable replay capsule with snapshot, trace, provenance hashes, and replay commands.
- [review-result.json](review-result.json): `kaios.review/v1`, the stable CLI/CI contract for the review command.

What to look for:

- `changedFiles.total == 1`: the review attached one bounded Kotlin change.
- `trace.status == valid` and `capsule.status == valid`: the evidence contracts passed.
- `replay.valid == true`: the capsule can be replayed offline without model access.
- `syscalls[]`: every tool call is recorded as an audit entry.
- `cost.estimatedCostMicros == 0`: the deterministic no-key provider has no money cost.

## Glossary

If the filenames in this folder do not mean much to you yet, here is a quick rundown:

- **Review artifact** (`change-review.md`) — A Markdown write-up of the agent run. It tells you which files were reviewed, what processes ran, and what the agent concluded. You can read it like a normal code-review comment.
- **Process trace** (`change-review.trace.json`) — The detailed record behind the review. Every process, every tool call, every token count and cost entry is captured here so you can dig into the specifics when the Markdown summary is not enough.
- **Replay capsule** (`change-review.capsule.json`) — A self-contained package you can carry to another machine and replay offline, no API key needed. It bundles the snapshot, the trace, and provenance hashes together so nothing gets lost in transit.
- **Review result** (`review-result.json`) — The stable CLI/CI contract. Automation tools read this instead of parsing Markdown or terminal output.

Two other terms you will see in the wider docs:

- **Baseline diff** — When you compare two capsules with `kaios diff`, the output highlights real behavior changes and ignores noise like timestamps. CI can use `--check` to block a merge when something actually changed.
- **Evidence summary** — A compact, PR-friendly report from `kaios evidence --summary`. One look tells you whether the run is clean.
- **Recovery dry-run** — A read-only report from `kaios recover --dry-run` that lists crashed processes and what you could do about them, without touching anything.

For the full glossary with schema links, see [Evidence Glossary](../../docs/PROOF_PACK.md#evidence-glossary).

After installing KAI OS, validate the capsule from this folder:

```bash
kaios capsule --file examples/evidence-sample/change-review.capsule.json --check
kaios replay --file examples/evidence-sample/change-review.capsule.json
```

Want a compact baseline/current diff sample without wiring a real model provider?

```bash
./gradlew installDist
./examples/evidence-sample/generate-baseline-diff.sh
```

The script creates a tiny disposable Git repo, captures two deterministic
`kaios review --json` runs, validates and replays both capsules, then runs:

```bash
kaios diff examples/evidence-sample/generated/baseline.capsule.json \
  examples/evidence-sample/generated/current.capsule.json \
  --check
```

Generated outputs to inspect:

- `generated/baseline.capsule.json`: the baseline review capsule.
- `generated/current.capsule.json`: the current review capsule with a small,
  stable input change.
- `generated/baseline-current.diff.txt`: the `kaios diff --check` output.
- `generated/baseline-review-result.json` and
  `generated/current-review-result.json`: the stable `kaios.review/v1` payloads
  for both runs.

What to look for in the baseline diff path:

- `replay.valid == true` for both baseline and current capsules.
- `kaios diff --check` exits `1` when stable runtime behavior changes.
- The diff output highlights reviewer-facing behavior changes instead of raw
  timestamps or run ids.

The equivalent live path in your own Git repository is:

```bash
kaios quickstart
kaios review
kaios evidence --summary
```

Feedback is useful even if you only inspected these files. Open an [Evidence feedback issue](https://github.com/morning-verlu/KAI/issues/new?template=evidence_feedback.yml) and tell us whether the trace, capsule, or process table would help in a real review or CI workflow.
