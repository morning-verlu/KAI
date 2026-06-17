# Post Now

Status: draft, not posted.

Use this page when you want the next manual launch action without reading every launch draft. For channel choice and source notes, see [community-targets.md](community-targets.md).
After publishing, use [follow-up-playbook.md](follow-up-playbook.md) for replies, metric checks, and channel-switch decisions.

Current diagnosis:

- X text-only distribution was too weak.
- GitHub repo and website conversion paths are ready.
- The best next channel is a focused Kotlin/JVM community post because KAI OS needs API-shape feedback from people who understand JVM infrastructure.

## Preflight

Run:

```bash
./scripts/launch-metrics.sh
```

Confirm the current public links:

```text
Evidence Viewer:
https://morning-verlu.github.io/KAI/evidence-viewer.html

Product-proof image:
https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png

Contributor Board:
https://github.com/morning-verlu/KAI/blob/main/docs/CONTRIBUTOR_BOARD.md

Kotlin/JVM evaluation path:
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_JVM_EVALUATION.md

Kotlin/JVM feedback discussion:
https://github.com/morning-verlu/KAI/discussions/17
```

## 1. Kotlin/JVM Community

Use this first for Kotlin Slack, Kotlin forum, JVM backend groups, or a Kotlin-focused Discord.

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to see the product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=kotlin_community&utm_medium=community&utm_campaign=post_now

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

The Kotlin API example is here:
https://github.com/morning-verlu/KAI/tree/main/examples/kotlin-runtime-api

Kotlin/JVM evaluation path:
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_JVM_EVALUATION.md

Kotlin/JVM feedback form:
https://github.com/morning-verlu/KAI/issues/new?template=kotlin_api_feedback.yml

Focused GitHub Discussion:
https://github.com/morning-verlu/KAI/discussions/17

Contributor board:
https://github.com/morning-verlu/KAI/blob/main/docs/CONTRIBUTOR_BOARD.md

I would especially like feedback on:

- whether the Agent = Process model is useful for JVM teams
- whether the Kotlin runtime API feels idiomatic
- whether replayable capsules and CI evidence gates would help maintainers trust agent reviews
```

After posting, record the URL in issue #7 and run:

```bash
./scripts/launch-metrics.sh
```

Then follow [follow-up-playbook.md](follow-up-playbook.md) at +2h, +24h, and +72h.

## 2. Show HN

Use this only after the Kotlin/JVM post is live or if there is no realistic Kotlin/JVM channel available.

Title:

```text
Show HN: KAI OS - Local-first Evidence OS for AI agents in Kotlin
```

URL:

```text
https://github.com/morning-verlu/KAI
```

Text:

```text
I am building KAI OS, a Kotlin/JVM runtime that turns AI agent runs into process traces, replayable capsules, syscall ledgers, and CI-ready runtime evidence.

The model is:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current release is small but runnable. The fastest way to understand it is the no-install Evidence Viewer:

https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=hacker_news&utm_medium=community&utm_campaign=post_now

It shows a checked-in KAI OS run as a process table, syscall ledger, replayable capsule, offline replay result, and baseline gate drift example.

The hands-on path is also no-key:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The point is not to be another Kotlin LangChain clone. I am trying to build the local evidence layer around agent work: what ran, which tools were called, what can be replayed offline, and what can be gated in CI.

I would love feedback from Kotlin/JVM developers, OSS maintainers, and people thinking about agent runtime infrastructure.
```

## 3. LinkedIn Visual

Use this if the maintainer account has an engineering audience.

Attach:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png
```

Post:

```text
Agent runs should not disappear after the answer.

I am building KAI OS: a local-first Evidence OS for AI agents in Kotlin.

It turns each run into evidence:

- process table
- syscall ledger
- replay capsule
- CI baseline gate

No API key and no install needed to inspect the current product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=linkedin&utm_medium=social&utm_campaign=post_now

Repo:
https://github.com/morning-verlu/KAI

Feedback welcome from Kotlin/JVM developers, OSS maintainers, and people building agent infrastructure.
```

## Record After Posting

Add a comment to issue #7:

```text
Manual post published:

- Channel:
- URL:
- UTM campaign: post_now
- Baseline metrics:
- Next check: +2h
```

Then capture follow-up metrics:

```bash
./scripts/launch-metrics.sh
```

## Reply Shortcuts

Why not LangChain/Koog/LangChain4j?

```text
KAI OS is lower-level. The bet is not more agent abstractions; it is portable runtime evidence: process traces, syscall records, replayable capsules, recovery evidence, and baseline diffs that can be inspected later or gated in CI.
```

Why Kotlin?

```text
The JVM already runs a lot of backend and CI infrastructure, and Kotlin gives the runtime a typed API plus DSL ergonomics. The project is trying to make agent evidence feel native to JVM teams rather than bolted on from Python.
```

Where can I inspect it without installing?

```text
Evidence Viewer:
https://morning-verlu.github.io/KAI/evidence-viewer.html

Checked-in evidence samples:
https://github.com/morning-verlu/KAI/tree/main/examples/evidence-sample
```
