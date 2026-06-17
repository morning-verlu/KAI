# Post-Publish Follow-Up Playbook

Status: operating checklist, not proof that anything has been posted.

Use this immediately after a Kotlin/JVM community post, Show HN post, Reddit
post, LinkedIn post, or X post goes live.

The goal is not to refresh metrics for comfort. The goal is to decide which
part of the launch loop failed:

```text
channel reach -> link click -> GitHub view -> star/fork/watch -> issue/discussion
```

## Minute 0

After posting:

1. Save the post URL in issue #7.
2. Run a baseline snapshot:

```bash
./scripts/launch-metrics.sh
```

3. Add this to issue #7:

```text
Manual post published:

- Channel:
- URL:
- UTM campaign:
- Baseline metrics:
- Next check: +2h
```

4. Stay available for replies for at least the first 30 minutes.

## First Replies

Use short, concrete replies. Link to the evidence, not a slogan.

If someone asks what it does:

```text
KAI OS packages an agent run as runtime evidence: process table, syscall ledger,
trace JSON, replayable capsule, and CI baseline diff.

No-install viewer:
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

If someone asks why not Koog or LangChain4j:

```text
Koog and LangChain4j are better fits for provider/application integration.
KAI OS is the evidence layer around a run: what ran, which tools were called,
what can be replayed offline, and what can be gated in CI.
```

If someone asks whether the Kotlin API is usable:

```text
That is the feedback I most want. The focused Kotlin/JVM evaluation path is:
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_JVM_EVALUATION.md

The discussion is here:
https://github.com/morning-verlu/KAI/discussions/17
```

## Plus 2 Hours

Capture:

```bash
./scripts/launch-metrics.sh
```

Decision:

| Signal | Meaning | Next action |
| --- | --- | --- |
| GitHub views stay at 0 | Distribution failed | Change channel before editing README again |
| GitHub views rise, stars stay 0 | Landing page or star rationale failed | Inspect README first screen and Evidence Viewer path |
| Stars rise, no issues/discussions | Interest but weak contribution path | Reply with Contributor Board and #17 |
| Comments ask "what is this?" | Positioning unclear | Reply with Evidence Viewer and process/syscall/capsule summary |
| Comments ask "why Kotlin?" | Audience is relevant | Reply with JVM/CI/runtime-boundary angle |

## Plus 24 Hours

Capture:

```bash
./scripts/launch-metrics.sh
```

If the post produced GitHub views but no stars:

- shorten the first sentence of the next post.
- lead with the product-proof image.
- link the Evidence Viewer before the repo.
- keep the same audience if comments were relevant.

If the post produced no GitHub views:

- do not edit product copy first.
- move to the next channel in [community-targets.md](community-targets.md).

If the post produced a question:

- answer it publicly.
- link the answer from #7 if it clarifies positioning.
- convert repeated confusion into a docs issue.

## Plus 72 Hours

Record a short readout in #7:

```text
72h post readout:

- Channel:
- URL:
- Stars/forks/watchers:
- GitHub views:
- Discussion/issue activity:
- Main objection:
- Next channel:
```

Then choose one:

- repeat the channel only if views and comments were relevant.
- switch to Show HN if Kotlin/JVM channels produced no reach.
- improve README/Evidence Viewer only if GitHub views rose but stars stayed flat.
- add contributor issues only if stars rose but nobody knew how to help.

## Stop Conditions

Do not keep reposting the same copy when:

- GitHub views remain 0.
- replies are about install mechanics instead of product value.
- the maintainer cannot answer comments for the first few hours.
- the same audience already saw the post in the last 24 hours.

The next useful move should be based on the weakest observed link in the chain,
not on the easiest file to edit.
