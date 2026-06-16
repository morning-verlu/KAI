# Show HN Post

Status: draft, not posted.

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

The core model is:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current v0.3.1 release is small but runnable. The fastest way to understand it is the no-install Evidence Viewer:

No install needed to see the product surface:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in KAI OS run as a process table, syscall ledger, replayable capsule, offline replay result, and baseline gate drift example.

Hands-on no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable Git repo, runs a deterministic current-change review, and writes a Markdown review artifact, process trace, replayable capsule, process table, evidence summary, and recovery dry-run report.

The point is not to be another Kotlin LangChain clone. I am trying to build the local evidence layer around agent work: what ran, which tools were called, what failed, what recovered, what can be replayed offline, and what can be gated in CI.

I would love feedback from Kotlin/JVM developers, OSS maintainers, and people thinking about agent runtime infrastructure.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```

First reply if someone asks for proof without installing:

```text
The no-install Evidence Viewer is here:

https://morning-verlu.github.io/KAI/evidence-viewer.html

The checked-in evidence samples are here:

https://github.com/morning-verlu/KAI/tree/main/examples/evidence-sample

That directory includes the Markdown review artifact, process trace JSON, replayable capsule, and kaios.review/v1 output.
```
