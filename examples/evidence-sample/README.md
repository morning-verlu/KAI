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

After installing KAI OS, validate the capsule from this folder:

```bash
kaios capsule --file examples/evidence-sample/change-review.capsule.json --check
kaios replay --file examples/evidence-sample/change-review.capsule.json
```

The equivalent live path in your own Git repository is:

```bash
kaios quickstart
kaios review
kaios evidence --summary
```

Feedback is useful even if you only inspected these files. Open an [Evidence feedback issue](https://github.com/morning-verlu/KAI/issues/new?template=evidence_feedback.yml) and tell us whether the trace, capsule, or process table would help in a real review or CI workflow.
