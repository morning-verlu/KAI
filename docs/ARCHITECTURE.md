# Architecture

KAI OS is an AI-native runtime, not a prompt-chain helper. The core design borrows operating-system concepts and applies them to agent execution.

## Core Model

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
```

An agent process has:

- `pid`
- `runId`
- `agent`
- lifecycle state
- token usage
- context size
- syscall count
- timestamps
- failure metadata

Runtime events create an inspectable trace for every run.

## Runtime

`runtime-core` owns lifecycle and scheduling:

- `AgentRuntime` creates and transitions processes.
- `WorkflowScheduler` executes a workflow DAG.
- `ModelProvider` abstracts model execution.
- `MockModelProvider` provides deterministic local behavior for tests and demos.
- `OpenAiCompatibleModelProvider` and `OllamaModelProvider` connect the same runtime boundary to real model APIs.
- `ToolRegistry` enforces syscall registration and agent permissions.

The first scheduler is deliberately small: it runs ready nodes in parallel-capable batches, records failure state, and can route a failed node to a declared fallback node.

## Tools as Syscalls

Agents never directly perform side effects. They request tool calls.

The tool system checks:

- the tool is registered
- the agent is allowed to call it
- the agent has the required permission

Built-in tools in v0.1:

- `echo`
- `clock`
- `mock-http`

## Memory and Snapshots

`memory-engine` provides:

- `SessionMemoryStore`
- `FileRunSnapshotStore`

Snapshots are JSON files under `.kaios/runs/` and are used by the CLI to inspect prior runs.

## CLI

`kaios-cli` exposes the runtime as a local developer experience:

- `kaios run "task"`
- `kaios ps <run-id>`
- `kaios inspect <run-id>`

`kaios-cli` defaults to `MockModelProvider`, but can select real providers through `KAIOS_MODEL_PROVIDER`.

The default workflow is:

```text
planner -> executor -> validator
```

## Future Direction

The intended long-term shape:

- JVM runtime core
- coroutine scheduler
- real model providers
- file/shell/http/db tools with sandbox policy
- SQLite and vector memory adapters
- plugin system using JVM classloaders
- Compose Desktop or web process manager
- visual workflow builder
