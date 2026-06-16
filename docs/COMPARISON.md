# KAI OS Compared

KAI OS is not trying to replace LangGraph, the OpenAI Agents SDK, Koog, or LangChain4j.

Those projects help teams build, orchestrate, or integrate agents and LLM applications. KAI OS focuses on a narrower runtime layer: local-first evidence for agent runs.

> The useful question is not "Which framework has more agents?" It is "Can this run be inspected, replayed as evidence, audited, compared, and used as a CI gate?"

## Short Version

Use KAI OS when you want portable runtime evidence around an agent run:

- process-style traces with PID, lifecycle, tokens, context, tool calls, tool time, cost fields, and denied syscalls
- replayable run capsules that can be copied to another machine and checked offline
- deterministic no-key demos through the mock provider
- Git change review artifacts that can become CI evidence
- syscall-style tool boundaries and audit records

Use the other frameworks when you need their provider ecosystem, hosted services, application framework, or mature production orchestration features.

KAI OS "proof" means a runtime evidence artifact. It does not prove that an agent's answer is correct. Offline replay validates and rebuilds evidence from saved snapshots and capsules; it does not re-execute real model calls, real network calls, or tool side effects.

## Comparison Table

| Project | Primary job | Strong fit | KAI OS difference |
| --- | --- | --- | --- |
| KAI OS | Local-first Evidence OS for agent runs | Agent review artifacts, process traces, replayable capsules, syscall ledgers, CI gates | Evidence is the product surface, not a side effect |
| LangGraph | Agent orchestration runtime | Durable execution, persistence, streaming, human-in-the-loop workflows | KAI OS is smaller and local-first; it packages portable runtime evidence around runs |
| OpenAI Agents SDK | Agent application SDK | Agents that plan, call tools, collaborate, keep state, use tracing, and apply guardrails | KAI OS can sit outside provider choice and show what happened in a run |
| Koog | Kotlin/JVM agent framework | Idiomatic Kotlin and Java agents, tool workflows, type-safe DSL, KMP direction | KAI OS is not another agent DSL first; it is a runtime evidence layer |
| LangChain4j | JVM LLM application library | Unified Java APIs for model providers, vector stores, tools, agents, MCP, and RAG | KAI OS does not compete on provider breadth; it focuses on audit, replay, and CI evidence |

## Where KAI OS Fits

KAI OS is best treated as an evidence layer below or beside agent applications:

```text
Agent framework / provider SDK
        |
        v
KAI OS evidence runtime
        |
        v
Trace + capsule + syscall ledger + CI gate
```

That means a team could use:

- LangGraph or OpenAI Agents SDK for agent orchestration and model execution
- Koog or LangChain4j for JVM-native agent and LLM integration
- KAI OS to package the run as inspectable evidence, replay the evidence offline, and compare it against a baseline

KAI OS v0.3.1 ships the local evidence loop first. Deeper integrations can come after the trace, capsule, syscall, and review contracts stay stable.

## Current Boundaries

KAI OS v0.3.1 is deliberately narrow:

- Offline replay validates saved evidence and rebuilds trace output; it does not replay live model or network side effects.
- Process recovery is exposed as recovery metadata and `kaios recover --dry-run`; it is not host-crash automatic continuation.
- The syscall layer is a capability-based tool boundary and audit ledger; it is not a replacement for container isolation, OS permissions, or a security audit.
- Cost fields are ledger-ready estimates. Money cost defaults to zero unless an explicit cost profile is configured.
- CI gates compare stable runtime evidence. They catch behavioral drift, but they do not certify model quality.

## Choose KAI OS When

- You want `kaios tour` to work without an API key, provider account, or network call.
- You need a review artifact plus machine-readable trace for a dirty Git change set.
- You want to audit tool calls as syscalls, including denied calls.
- You want a capsule that another maintainer can replay offline.
- You want a CI gate based on runtime evidence instead of only source diff or unit tests.
- You are building Kotlin/JVM agent infrastructure and want process boundaries before adding providers, UI, or plugins.

## Do Not Choose KAI OS When

- You mainly need a chatbot UI.
- You mainly need many model providers out of the box.
- You need a hosted tracing or evaluation platform today.
- You need mature distributed execution across a real cluster today.
- You need a visual workflow builder today.

Those are valid needs. They are just not the v0.3.1 wedge.

## Evidence Core Moat

KAI OS uses the operating-system metaphor as implementation pressure, not only branding:

- Agent = process: PID, lifecycle, suspend/resume, failure kind, recovery PID, memory scope
- Workflow = scheduler: DAG order, priority, retry, recovery, fallback, event triggers
- Tool = syscall: capability grants, permission checks, audit records, duration, cost fields
- Run = evidence: Markdown artifact, JSON trace, replayable capsule, baseline diff, CI summary

The goal is to make agent work reviewable in the same practical way source code is reviewable: small enough to inspect, stable enough to compare, and portable enough to share safely.

## Source Notes

These comparisons are based on each project's public positioning:

- [LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)
- [OpenAI Agents guide](https://developers.openai.com/api/docs/guides/agents)
- [OpenAI Agents tracing](https://openai.github.io/openai-agents-python/tracing/)
- [OpenAI Agents guardrails](https://openai.github.io/openai-agents-python/guardrails/)
- [Koog documentation](https://docs.koog.ai/)
- [Koog GitHub repository](https://github.com/JetBrains/koog)
- [LangChain4j documentation](https://docs.langchain4j.dev/)
- [LangChain4j GitHub repository](https://github.com/langchain4j/langchain4j)
