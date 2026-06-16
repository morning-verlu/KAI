# Evidence OS For Agents

KAI OS chooses one main moat: local-first evidence for agent work.

The product claim is:

> KAI OS is the local-first Evidence OS that turns agent runs into process traces, replayable capsules, and CI-ready runtime evidence.

This is different from building another Kotlin prompt framework. The runtime still has agents, workflows, tools, memory, and providers, but the product path is evidence:

```bash
kaios quickstart
kaios review
kaios evidence --baseline artifacts/baseline.capsule.json --check
```

## Why Evidence Is The Product

Agent output alone is hard to trust. A useful production agent system needs to answer:

- What agent processes ran?
- What state transitions happened?
- How many tokens, context bytes, and syscalls were used?
- Which changed files were attached as bounded context?
- Can another machine replay the run offline?
- Did stable runtime behavior change from the baseline?
- Can CI fail on drift without calling a model provider again?

KAI OS treats those answers as first-class runtime output, not as an observability add-on.

## v0.3 Evidence Core

The shipped v0.3 moat is three runtime primitives that show up directly in trace, capsule, review, and evidence output:

- Process Recovery: runtime crashes are recorded as `FAILED + RUNTIME_CRASH`, recovery starts a new PID, and `kaios recover --dry-run` reports which processes are recoverable.
- Priority Scheduler: ready DAG nodes run by priority, event-triggered nodes wait for matching runtime events, and the local worker backend records `workerId` without requiring a real cluster.
- Syscall Ledger: every tool call produces an audit record with allowed/denied status, redacted arguments, tool duration, estimated cost, and denied syscall counts.

## The Evidence Chain

`kaios review` is the main product loop for day-to-day development:

```bash
kaios review
```

It detects the current Git change set, creates a Workspace Index, attaches bounded changed-file context, runs the deterministic review workflow, and writes:

- `artifacts/change-review.md`: human-readable review artifact.
- `artifacts/change-review.trace.json`: `kaios.process-trace/v1` process trace.
- `artifacts/change-review.capsule.json`: portable run capsule with embedded snapshot and trace.

For automation:

```bash
kaios review --json
```

The JSON schema is `kaios.review/v1`. It reports status, run id, changed files, artifact path, trace path, capsule path, replay result, optional baseline diff, and stable next actions.

For gates:

```bash
kaios review --baseline artifacts/baseline.capsule.json --check
kaios evidence --summary
```

The baseline check exits non-zero when stable runtime behavior changes. The summary command prints PR-friendly Markdown with Verdict, Changed Runtime Behavior, Fix First, and Process Table sections.

For recovery inspection:

```bash
kaios recover latest --dry-run
```

v0.3 recovery is intentionally dry-run only. It does not mutate snapshots or re-run agents; it explains the crashed processes, recovery evidence, and next commands.

## Local-First Trust Boundary

The default path is intentionally boring:

- no API key required.
- no network required.
- deterministic `MockModelProvider`.
- `.kaiosignore` respected before files reach agent context.
- secrets are not intentionally printed by diagnostics or review artifacts.
- capsules can be copied to another machine and replayed offline.

Before running review in a private repository, add local exclusions:

```gitignore
.env
*.pem
secrets/
private/
```

## What KAI OS Does Not Prioritize Yet

The moat is not a chat UI, visual workflow builder, plugin marketplace, or more model providers.

Those can matter later, but they do not validate the core product. The core product is that a developer can run one local command and get evidence that is inspectable, portable, replayable, and gateable.

## Positioning Against The Market

Many agent systems are moving toward production runtime concerns:

- [LangGraph](https://docs.langchain.com/oss/python/langgraph/overview) documents durable execution, persistence, human-in-the-loop, and observability.
- [OpenAI Agents SDK tracing](https://openai.github.io/openai-agents-python/tracing/) and [guardrails](https://openai.github.io/openai-agents-python/guardrails/) make traces and safety controls explicit.
- [CrewAI tracing](https://docs.crewai.com/en/observability/tracing) and [AutoGen observability](https://microsoft.github.io/autogen/0.2/docs/topics/llm-observability/) show that monitoring is now expected.
- Kotlin and JVM teams already have agent or LLM libraries such as [Koog](https://docs.koog.ai/) and [LangChain4j](https://github.com/langchain4j/langchain4j).

KAI OS should not compete by claiming "more agents" or "more integrations" first. It should compete by making evidence portable and local-first.

For a more detailed framework-by-framework view, read [KAI OS Compared](COMPARISON.md).

| Axis | KAI OS | LangGraph / OpenAI Agents / CrewAI | Koog / LangChain4j |
| --- | --- | --- | --- |
| Primary identity | Evidence OS for agent runs | Agent runtime or agent framework | JVM/Kotlin LLM and agent development |
| Default demo | Local deterministic review and replay | Provider or framework integrated examples; deployment varies by stack | Library and framework integration examples |
| First artifact | Markdown review, process trace, capsule | Trace or runtime output, depending on stack | Application code and model/tool abstractions |
| Offline replay | Core product path through capsules | Varies by stack and deployment | Not usually the main product surface |
| CI gate | `review --baseline --check` and `evidence --summary` | Possible through integrations | Built by the application team |
| Trust posture | No API key, local-first by default | Often provider and service integrated | Depends on app integration |

This is the wedge: KAI OS can be small and still valuable if it owns the evidence layer well.

## Current Success Criteria

KAI OS v0.3.1 should be judged by the review evidence loop, not by feature count:

- A dirty Git repo can run `kaios review` and get artifact, trace, capsule, replay result, and stable JSON.
- A clean Git repo fails clearly and recommends executable next commands.
- `.kaiosignore` prevents sensitive paths from reaching review artifacts.
- A capsule can be copied to another machine and replayed without model access.
- A baseline check can fail CI when stable runtime behavior changes.
- A developer immediately understands: this is not a chatbot, it is the agent runtime evidence layer.
