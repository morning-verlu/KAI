# First External Wave

Status: ready to post, not posted by this file.

Goal: get the first real feedback and first real stars from people likely to understand the Evidence OS pitch.

Primary CTA:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

Secondary CTA:

```text
https://github.com/morning-verlu/KAI
```

## Posting Order

1. X or LinkedIn short post.
2. Kotlin/JVM community post.
3. Show HN after the first two posts are live or after the repo receives early feedback.

Do not publish every draft everywhere at once. Post, watch replies, then adjust the next post.

## Post 1: X / LinkedIn Short

Use this first because it is short, visual, and links to the no-install viewer.

```text
I am building KAI OS: a local-first Evidence OS for AI agents in Kotlin.

The point is not another chatbot framework. The point is runtime evidence:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to inspect a checked-in run:
https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a process table, syscall ledger, replayable capsule, and baseline gate.

Repo:
https://github.com/morning-verlu/KAI
```

Attach when the platform supports images:

```text
https://morning-verlu.github.io/KAI/assets/kaios-social-card.png
```

## Post 2: Kotlin / JVM Community

Use this for Kotlin Slack, Kotlin forum, JVM backend groups, or a Kotlin-focused Discord.

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to see the product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html

The viewer shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

The Kotlin/JVM library path is here:
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_API.md

And the no-key tour is:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

I would especially like feedback on the Kotlin API shape, the process/scheduler/tool model, and whether local evidence capsules are useful for JVM backend teams.
```

## Post 3: Show HN

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

The fastest way to understand it is the no-install Evidence Viewer:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in KAI OS run as a process table, syscall ledger, replayable capsule, offline replay result, and baseline gate drift example.

The hands-on path is also no-key:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The point is not to be another Kotlin LangChain clone. I am trying to build the local evidence layer around agent work: what ran, which tools were called, what can be replayed offline, and what can be gated in CI.

I would love feedback from Kotlin/JVM developers, OSS maintainers, and people thinking about agent runtime infrastructure.

Repo:
https://github.com/morning-verlu/KAI
```

## First Reply Template

Use this when someone asks why KAI OS is different:

```text
The difference is the evidence layer.

KAI OS records process traces, replayable capsules, syscall audit records, recovery evidence, and baseline diffs so agent runs can be inspected or gated later without relying on provider logs.

No-install viewer:
https://morning-verlu.github.io/KAI/evidence-viewer.html

Trust matrix:
https://github.com/morning-verlu/KAI/blob/main/docs/TRUST_MATRIX.md
```

## Metrics To Capture

Capture these before posting, then again 2 hours, 24 hours, and 72 hours after posting:

```bash
gh repo view morning-verlu/KAI --json stargazerCount,forkCount,watchers,usesCustomOpenGraphImage
```

Also note:

- post URLs.
- top questions or objections.
- whether people clicked the Evidence Viewer or asked for screenshots.
- whether Kotlin/JVM feedback is about API shape, runtime model, or install friction.
- any issue or PR opened by an external contributor.

## Current Baseline

As of the first-wave prep pass:

```text
stars: 0
forks: 2
watchers: 0
social preview: uploaded
```

## Posted Links

- X short post: https://x.com/wurslu/status/2066846887983096042 (posted 2026-06-16)
