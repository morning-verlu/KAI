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
- `WorkflowScheduler` executes a workflow DAG with coroutine-based structured concurrency, priority-ordered ready nodes, local worker leases, event triggers, retry, fallback, and recovery policy.
- `ModelProvider` abstracts model execution.
- `MockModelProvider` provides deterministic local behavior for tests, demos, and project-aware no-key summaries when Workspace Index or context input is attached.
- `OpenAiCompatibleModelProvider` and `OllamaModelProvider` connect the same runtime boundary to real model APIs.
- `ToolRegistry` enforces syscall registration, capability grants, agent permissions, scope/limit checks, audit records, and cost ledger updates.

The scheduler runs ready nodes in coroutine batches, records failure and cancellation state, retries nodes with an observable retry policy, recovers crashed nodes into new PIDs when policy allows it, can route a failed node to a declared fallback node, and can enforce per-node timeouts.

## Tools as Syscalls

Agents never directly perform side effects. They request tool calls.

The tool system checks:

- the tool is registered
- the agent is allowed to call it
- the agent has the required permission
- the capability scope and limits allow the call

Every call writes a syscall audit record, including denied calls. The trace and capsule carry tool duration, denied syscall counts, and estimated cost counters so CI can reason about runtime behavior without scraping logs.

Built-in tools in v0.1:

- `echo`
- `clock`
- `mock-http`
- `http`
- `file`

Real HTTP syscalls are disabled unless `KAIOS_HTTP_ALLOWLIST` grants an exact host, wildcard host, or URL prefix. This keeps agent network IO explicit and inspectable.

The `file` syscall is rooted to a configured directory, defaults to `.kaios/files`, rejects absolute paths, and rejects normalized paths that escape the scoped root.

## Memory and Snapshots

`memory-engine` provides:

- `SessionMemoryStore`
- `SQLiteMemoryStore`
- `FileRunSnapshotStore`

Snapshots are JSON files under `.kaios/runs/` and are used by the CLI to inspect prior runs.

`SessionMemoryStore` is the default for short local runs. `SQLiteMemoryStore` persists memory entries to a local database for longer-running local runtimes. Memory can be scoped by agent, process attempt, or workflow, which lets recovered processes avoid inheriting a failed PID's context when `PROCESS` isolation is configured.

## CLI

`kaios-cli` exposes the runtime as a local developer experience:

- `kaios demo`
- `kaios run "task"`
- `kaios analyze [path ...]`
- `kaios index [path ...]`
- `kaios run --index . "task"`
- `kaios run --index . --trace-out artifacts/trace.json "task"`
- `kaios run --context README.md "task"`
- `kaios context [path ...]`
- `kaios runs` and `kaios runs --json`
- `kaios ps`
- `kaios inspect`
- `kaios trace`
- `kaios trace --json`
- `kaios trace --json --out artifacts/trace.json --force`
- `kaios evidence --out artifacts/run.capsule.json --force`
- `kaios report`
- `kaios export`

`kaios-cli` defaults to `MockModelProvider`, but can select real providers through `KAIOS_MODEL_PROVIDER`.

Reports are static HTML files generated from JSON snapshots under `.kaios/runs/`. They render a run list, process table, workflow graph, lifecycle event timeline, and final output without a web server. `kaios runs --json` emits `kaios.runs/v1`, a stable run registry for Agent Desktop, CI, and local tooling.

Process traces are text or JSON views generated from the same snapshots. `kaios trace` emits `kaios.process-trace/v1` with process metrics, the observed execution path, event counts, and lifecycle timeline for CI, replay, visualizers, audit logs, and future Agent Desktop surfaces. See [TRACE.md](TRACE.md) for the schema contract and output-file workflow.

Run capsules are portable JSON evidence packages generated from the same snapshots. `kaios capsule` emits `kaios.run-capsule/v1` with the full snapshot, full process trace, provenance hashes, config validation metadata, replay commands, and contract validation status. `kaios replay` emits `kaios.run-replay/v1` after rebuilding the trace from the embedded snapshot, so a shared capsule can prove its runtime evidence without API keys or the original run directory. `kaios diff` emits `kaios.run-diff/v1` after comparing two replay-validated capsules by stable runtime signature instead of run ids or timestamps. `kaios evidence` emits `kaios.evidence/v1` as the product-level gate that writes the capsule, validates it, replays it, and optionally runs the baseline diff in one command. See [CAPSULE.md](CAPSULE.md) for the schema contract.

Artifacts are Markdown files generated with `kaios run --out <path>` or `kaios export`. They are designed for handoff into issues, pull requests, docs, and release notes, and default to `.kaios/artifacts/<run-id>.md`.

Workspace Analysis and Workspace Index are generated by the CLI before scheduling. `kaios analyze [path ...]` writes a deterministic Markdown or JSON project report with stack signals, architecture map, hotspots, quality signals, and suggested KAI OS commands. `kaios index [path ...]` builds a source map with language stats, top directories, notable files, line counts, and largest readable text files. `kaios run --index <path>` injects that compact source map into the workflow input and stores a summary in snapshots and artifacts.

Context files are loaded by the CLI before scheduling. `kaios context [path ...]` previews the bounded file set, and `kaios run --context <file-or-dir>` passes the same bounded payload into the workflow input. Index and context loading expand readable text inside the current workspace, honor `.kaiosignore`, skip generated/runtime directories such as `artifacts`, and enforce limits. Snapshots and artifacts persist source summaries so the run remains inspectable without dumping full project files into every handoff.

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
