# Kotlin/JVM Evaluation Path

Use this page when you are evaluating KAI OS from a Kotlin, JVM backend, CI,
or agent-infrastructure point of view.

KAI OS is not trying to be a Kotlin LangChain clone. The bet is narrower:
agent runs should leave local runtime evidence that another developer, reviewer,
or CI job can inspect later.

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
Run      = Evidence
```

## Start Without Installing

Open the no-install Evidence Viewer first:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

Look for:

- process rows with PID, state, token, memory, syscall, tool-time, and cost fields.
- syscall ledger entries with allowed or denied tool calls.
- a replayable capsule contract.
- an offline replay result.
- a baseline gate example where stable runtime behavior changed.

This is the fastest way to decide whether the evidence surface is useful before
running Java, Gradle, Docker, or a model provider.

## Run The Kotlin API Example

If you want the embeddable JVM runtime shape, run:

```bash
./gradlew -p examples/kotlin-runtime-api run
```

The example demonstrates:

- `AgentSpec` as a process definition.
- `WorkflowScheduler` as a priority-aware DAG scheduler.
- `ToolRegistry` as the syscall boundary.
- `ToolCapabilityGrant` for permissioned tool access.
- scoped memory through `SessionMemoryStore`.
- deterministic no-key execution through `MockModelProvider`.
- local worker simulation through `LocalWorkerExecutorBackend`.

Read the API guide:

```text
https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_API.md
```

## Try A Realistic JVM Review

If you want to see the CLI review path on a tiny backend change, use:

```text
https://github.com/morning-verlu/KAI/tree/main/examples/jvm-service-review
```

That example shows how `kaios review` turns a Kotlin/JVM service change into:

- Markdown review artifact.
- process trace JSON.
- replayable capsule JSON.
- `kaios.review/v1` output.
- process table and evidence summary.

## Compare It Honestly

Use Koog or LangChain4j when you need their JVM-native provider breadth, agent
application APIs, RAG integrations, or mature framework surface.

Use KAI OS when the missing layer is evidence:

- What processes ran?
- Which tools were called?
- Which syscalls were denied?
- What can be replayed offline?
- What can be compared against a baseline in CI?
- What can be shared with another maintainer without handing them provider logs?

Comparison notes:

```text
https://github.com/morning-verlu/KAI/blob/main/docs/COMPARISON.md
```

## What Feedback Helps Most

For Kotlin/JVM developers, the highest-value feedback is specific:

- Does the runtime API feel idiomatic Kotlin?
- Is `Agent = Process` a useful mental model for JVM teams?
- Are capability grants and syscall ledgers the right tool boundary?
- Would replayable capsules help PR review, CI, support, or audits?
- What would stop you from trying `kaios review` on a real JVM repo?

Open feedback here:

```text
https://github.com/morning-verlu/KAI/issues/new?template=kotlin_api_feedback.yml
```

Join the focused GitHub Discussion:

```text
https://github.com/morning-verlu/KAI/discussions/17
```

## Small Ways To Help

If the direction is useful:

- Star the repo so the Kotlin/JVM evidence-runtime idea gets a visible signal.
- Share the Evidence Viewer with one JVM backend developer and capture their first question.
- Pick a small task from the Contributor Board:

```text
https://github.com/morning-verlu/KAI/blob/main/docs/CONTRIBUTOR_BOARD.md
```

Good first follow-ups right now:

- custom tool capability recipe: https://github.com/morning-verlu/KAI/issues/14
- denied-syscall evidence walkthrough: https://github.com/morning-verlu/KAI/issues/15
- full Docker smoke verification: https://github.com/morning-verlu/KAI/issues/16
