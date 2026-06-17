# Kotlin Community Post

Status: draft, not posted.

Use for Kotlin Slack, Kotlin forum, or JVM-focused communities. Adjust the opening sentence to fit the channel norms.

## Short Post

Use this when the channel favors concise posts:

```text
I am building KAI OS, a Kotlin/JVM Evidence OS for AI agent runs.

Agent = Process
Workflow = Scheduler
Tool = Syscall
Run = Evidence

It is not trying to be a Kotlin LangChain clone. It focuses on local runtime evidence: process traces, syscall ledgers, replayable capsules, and CI baseline gates.

No install needed:
https://morning-verlu.github.io/KAI/evidence-viewer.html?utm_source=kotlin_community&utm_medium=community&utm_campaign=post_now

Kotlin/JVM evaluation path:
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_JVM_EVALUATION.md

Focused feedback discussion:
https://github.com/morning-verlu/KAI/discussions/17

I would love feedback on whether the API shape feels idiomatic Kotlin and whether replayable capsules would help JVM maintainers trust agent reviews.
```

## Full Post

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

No install needed to see the product surface:

https://morning-verlu.github.io/KAI/evidence-viewer.html

It shows a checked-in run as a process table, syscall ledger, replayable capsule, and baseline gate.

The v0.3.1 release also has a no-key first-run tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

It generates a process table, trace JSON, replayable capsule, current-change review artifact, and CI-style evidence summary from a disposable local repo.

There is also a Kotlin Runtime API example:

./gradlew -p examples/kotlin-runtime-api run

I would especially like feedback on the Kotlin API shape, the process/scheduler/tool model, and whether local evidence capsules are useful for JVM backend teams.

Repo: https://github.com/morning-verlu/KAI
Kotlin/JVM evaluation path: https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_JVM_EVALUATION.md
Focused feedback discussion: https://github.com/morning-verlu/KAI/discussions/17
Kotlin API: https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_API.md
Start here: https://github.com/morning-verlu/KAI/blob/main/START_HERE.md
```

Follow-up reply if someone asks what makes it different:

```text
The difference is the evidence layer. KAI OS records process traces, replayable capsules, syscall audit records, recovery evidence, and baseline diffs so agent runs can be inspected or gated later without relying on provider logs.
```
