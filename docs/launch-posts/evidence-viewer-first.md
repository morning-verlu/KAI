# Evidence Viewer-First Posts

Status: draft, not posted.

Use these when the audience is cold and unlikely to run an install command before understanding the product. The first CTA is the no-install Evidence Viewer; the second CTA is the repo or tour.

## One-Line Post

```text
KAI OS turns AI agent runs into process traces, syscall ledgers, replayable capsules, and CI gates.

No install needed to see the product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html

Repo:
https://github.com/morning-verlu/KAI
```

## Short Social

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

## X / LinkedIn Thread

```text
1/ I am building KAI OS: a local-first Evidence OS for AI agents in Kotlin.

The core idea: agent work should leave inspectable runtime evidence, not just a chat transcript.

2/ Model:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

3/ No install needed to see the product surface:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in run as:

- process table
- syscall ledger
- replayable capsule
- offline replay status
- baseline gate drift example

4/ Hands-on path after that:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

5/ The moat is evidence:

- process traces
- capability-style tool calls
- syscall audit records
- replayable capsules
- stable baseline diffs
- CI-grade summaries

6/ Feedback welcome from Kotlin/JVM developers, OSS maintainers, and people building agent infrastructure.

Repo:
https://github.com/morning-verlu/KAI
```

## Show HN Text

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

## Kotlin/JVM Community Post

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

## First Reply

```text
The shortest star test:

If you need agent work to leave portable evidence that another developer, reviewer, or CI job can inspect without trusting opaque provider logs, KAI OS is probably worth watching.

Why star:
https://github.com/morning-verlu/KAI/blob/main/docs/WHY_STAR.md

Trust matrix:
https://github.com/morning-verlu/KAI/blob/main/docs/TRUST_MATRIX.md
```
