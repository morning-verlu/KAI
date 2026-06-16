# Community Wave

Status: draft, not posted.

Use this after the visual X post or instead of it if X still has weak distribution. The goal is real feedback from people who understand Kotlin/JVM, CI, observability, and agent infrastructure.

Do not post the same text everywhere. Match the community, disclose that it is early, and ask for specific feedback.

## Tracking Links

Use channel-specific Evidence Viewer links so the metrics readout can separate distribution paths:

```text
Kotlin/JVM:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=kotlin_community&utm_medium=community&utm_campaign=community_wave

Reddit Kotlin:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=reddit_kotlin&utm_medium=community&utm_campaign=community_wave

Hacker News:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=hacker_news&utm_medium=community&utm_campaign=community_wave

LinkedIn:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=linkedin&utm_medium=social&utm_campaign=community_wave
```

Repo:

```text
https://github.com/morning-verlu/KAI
```

Product-proof image:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png
```

## Posting Order

1. Kotlin/JVM community: best fit for API shape and JVM runtime feedback.
2. Reddit Kotlin/JVM: only after checking the community rules and choosing the discussion-oriented version.
3. Hacker News Show HN: only when the README, Evidence Viewer, and no-key tour are stable.
4. LinkedIn: use the visual version if you have an engineering audience there.

If a post gets real questions, pause cross-posting and answer those first.

## Kotlin/JVM Version

Use for Kotlin Slack, Kotlin forum, JVM backend groups, or Kotlin-focused Discords.

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to see the product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=kotlin_community&utm_medium=community&utm_campaign=community_wave

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

The Kotlin API example is here:
https://github.com/morning-verlu/KAI/tree/main/examples/kotlin-runtime-api

I would especially like feedback on:

- whether the Agent = Process model is useful for JVM teams
- whether the Kotlin runtime API feels idiomatic
- whether replayable capsules and CI evidence gates would help maintainers trust agent reviews
```

## Reddit Kotlin/JVM Version

Use a discussion-oriented title, not a marketing title.

Title:

```text
Would a Kotlin/JVM Evidence OS for AI agent runs be useful?
```

Post:

```text
I am building KAI OS, a small Kotlin/JVM runtime that treats AI agent runs like inspectable processes instead of one-off chat outputs.

The model is:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The main thing I am testing is whether JVM teams need local evidence artifacts around agent work: process traces, syscall ledgers, replayable capsules, and CI baseline gates.

No install is needed to inspect the current product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=reddit_kotlin&utm_medium=community&utm_campaign=community_wave

The repo is here:
https://github.com/morning-verlu/KAI

It is early and local-first. I am not claiming it is production complete. I would like feedback on whether the runtime model and Kotlin API shape are worth continuing.
```

## Show HN Version

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

https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=hacker_news&utm_medium=community&utm_campaign=community_wave

It shows a checked-in KAI OS run as a process table, syscall ledger, replayable capsule, offline replay result, and baseline gate drift example.

The hands-on path is also no-key:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The point is not to be another Kotlin LangChain clone. I am trying to build the local evidence layer around agent work: what ran, which tools were called, what can be replayed offline, and what can be gated in CI.

I would love feedback from Kotlin/JVM developers, OSS maintainers, and people thinking about agent runtime infrastructure.
```

## LinkedIn Visual Version

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
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=linkedin&utm_medium=social&utm_campaign=community_wave

Repo:
https://github.com/morning-verlu/KAI

Feedback welcome from Kotlin/JVM developers, OSS maintainers, and people building agent infrastructure.
```

## Reply Templates

If someone asks "why not LangChain/Koog/LangChain4j?":

```text
KAI OS is lower-level. The bet is not "more agent abstractions"; it is portable runtime evidence: process traces, syscall records, replayable capsules, recovery evidence, and baseline diffs that can be inspected later or gated in CI.
```

If someone asks "why Kotlin?":

```text
The JVM already runs a lot of backend and CI infrastructure, and Kotlin gives the runtime a typed API plus DSL ergonomics. The project is trying to make agent evidence feel native to JVM teams rather than bolted on from Python.
```

If someone asks for proof without installing:

```text
The no-install Evidence Viewer is here:
https://morning-verlu.github.io/KAI/evidence-viewer.html

The checked-in evidence samples are here:
https://github.com/morning-verlu/KAI/tree/main/examples/evidence-sample
```
