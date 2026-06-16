# Evaluate KAI OS In 5 Minutes

Use this page when you arrive from a post, release, or README and want to decide quickly whether KAI OS is worth a star, a trial, or a deeper review.

KAI OS is a local-first Evidence OS for AI agents in Kotlin:

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
```

The useful question is not "does it chat?" The useful question is:

```text
Can an agent run leave process evidence that I can inspect, replay, compare, and gate later?
```

## Path 1: Inspect Evidence Without Installing

Open the visual [Evidence Viewer](https://morning-verlu.github.io/KAI/evidence-viewer.html) for process table, syscall ledger, capsule, replay, and baseline gate panels.

Open the checked-in [Evidence Sample](../examples/evidence-sample/).

Look for:

- `change-review.md`: human-readable review artifact.
- `change-review.trace.json`: `kaios.process-trace/v1` process and syscall evidence.
- `change-review.capsule.json`: replayable `kaios.run-capsule/v1`.
- `review-result.json`: stable `kaios.review/v1` CLI/CI output.

This is the fastest way to understand the product surface before trusting any install path.

## Path 2: Try The Full Loop With No API Key

Local install:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
```

No local Java or Gradle:

```text
https://codespaces.new/morning-verlu/KAI?quickstart=1
```

Then run:

```bash
./scripts/codespaces-smoke.sh
```

What should happen:

- a disposable Git workspace is created.
- `kaios review` runs against a tiny local change.
- a Markdown review, process trace, replayable capsule, evidence summary, and recovery report are written.
- no external model provider or API key is required.

## Path 3: Evaluate The Kotlin Runtime API

Run the embeddable Kotlin/JVM example:

```bash
./gradlew -p examples/kotlin-runtime-api run
```

Look for:

- typed `AgentSpec` process definitions.
- priority-aware `WorkflowScheduler`.
- `ToolRegistry` syscall boundary.
- `ToolCapabilityGrant` permission checks.
- process table and syscall ledger output.

API guide: [Kotlin Runtime API](KOTLIN_API.md).

## What To Judge

KAI OS is promising if you care about:

- agent runs as inspectable process traces.
- local replay before provider integration.
- syscall audit records for tool use.
- CI gates over stable runtime behavior.
- Kotlin/JVM agent infrastructure with explicit runtime boundaries.

KAI OS is probably not the right fit if you only need:

- a chatbot UI.
- a prompt wrapper.
- a hosted agent product.
- a mature provider marketplace today.

## One-Minute Star Test

If the following sentence matches a problem you have, the repo is worth starring and watching:

```text
I need agent work to leave portable evidence that another developer, reviewer, or CI job can inspect without trusting opaque provider logs.
```

Next links:

- [Start here](../START_HERE.md)
- [Evidence OS](EVIDENCE_OS.md)
- [Kotlin API](KOTLIN_API.md)
- [Comparison](COMPARISON.md)
- [Launch discussion](https://github.com/morning-verlu/KAI/discussions/8)
