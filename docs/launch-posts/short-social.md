# Short Social Post

Status: draft, not posted.

Attach:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```

Post:

```text
I built KAI OS: a local-first Evidence OS for AI agents in Kotlin.

It treats agent work like runtime evidence:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to see the product surface:
https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

Hands-on no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable repo and outputs a process table, Markdown review, process trace, replayable capsule, and CI-style evidence summary.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```
