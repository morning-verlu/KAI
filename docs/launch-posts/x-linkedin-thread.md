# X / LinkedIn Thread

Status: draft, not posted.

Attach to the first post when supported:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```

Thread:

```text
1/ I built KAI OS: a local-first Evidence OS for AI agents in Kotlin.

The idea: agent work should be inspectable like operating-system processes, not just text from a chatbot.

2/ The runtime model:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

3/ No install needed to see the product surface:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

4/ The first-run path needs no API key:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

5/ `kaios tour` creates a disposable Git repo, runs a current-change review, and writes:

- Markdown review artifact
- process trace
- replayable capsule
- process table
- evidence summary
- recovery dry-run report

6/ The moat is evidence, not more agents:

- process recovery evidence
- priority scheduler evidence
- syscall audit ledger
- offline replay
- baseline diff gates
- PR-friendly evidence summaries

7/ I would love feedback from Kotlin/JVM developers, OSS maintainers, and people building agent infrastructure.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```
