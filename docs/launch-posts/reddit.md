# Reddit Post

Status: draft, not posted.

Suggested subreddits: start with Kotlin/JVM-specific communities. Broader programming or AI communities should wait until there is feedback from the first post.

Title:

```text
KAI OS - local-first Evidence OS for AI agents in Kotlin
```

Post:

```text
I am building KAI OS, a Kotlin/JVM runtime that treats AI agent runs like inspectable processes.

The model:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current release is intentionally small and local-first. It has a deterministic no-key tour:

No install needed to see the product surface:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

Hands-on no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable Git repo and outputs a current-change review artifact, process table, trace JSON, replayable capsule, evidence summary, and recovery dry-run report.

The main difference from agent frameworks is the focus on portable evidence: replay, baseline diffs, syscall audit records, process recovery evidence, and CI-ready summaries.

I would love feedback, especially from Kotlin/JVM developers and maintainers who care about agent runs being auditable and reproducible.

Repo: https://github.com/morning-verlu/KAI
Start here: https://github.com/morning-verlu/KAI/blob/main/START_HERE.md
```

Attach when image posts are useful:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```
