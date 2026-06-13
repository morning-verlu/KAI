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
- `file`

The `file` syscall is rooted to a configured directory, defaults to `.kaios/files`, rejects absolute paths, and rejects normalized paths that escape the scoped root.

## Memory and Snapshots

`memory-engine` provides:

- `SessionMemoryStore`
- `SQLiteMemoryStore`
- `FileRunSnapshotStore`

Snapshots are JSON files under `.kaios/runs/` and are used by the CLI to inspect prior runs.

`SessionMemoryStore` is the default for short local runs. `SQLiteMemoryStore` persists memory entries to a local database for longer-running local runtimes.

## CLI

`kaios-cli` exposes the runtime as a local developer experience:

- `kaios run "task"`
- `kaios runs`
- `kaios ps <run-id>`
- `kaios inspect <run-id>`
- `kaios report <run-id>`

`kaios-cli` defaults to `MockModelProvider`, but can select real providers through `KAIOS_MODEL_PROVIDER`.

Reports are static HTML files generated from JSON snapshots under `.kaios/runs/`. They render a run list, process table, workflow graph, lifecycle event timeline, and final output without a web server.

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
