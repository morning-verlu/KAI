# Roadmap

KAI OS is starting with a small, inspectable runtime. The goal is to grow into a Kotlin-native operating layer for AI agents.

## v0.1 - Runtime Seed

- Kotlin/JVM multi-module project
- deterministic mock model provider
- process lifecycle and metrics
- DAG workflow scheduler
- permissioned syscall tools
- session memory
- SQLite memory adapter
- JSON run snapshots
- CLI run, process table, and inspector
- static Agent Process Manager report

## v0.2 - Real Providers and Safer Tools

- OpenAI-compatible provider foundations, shipped in v0.1.1
- Ollama provider foundations, shipped in v0.1.1
- scoped file syscall, shipped in v0.1.3
- SQLite memory adapter, shipped in v0.1.4
- structured tool argument schemas
- HTTP tool with allowlist policy
- better error surfaces and retry policy

## v0.3 - Scheduler Kernel

- coroutine-based execution
- cancellation propagation
- timeout and budget policies
- richer fallback routing
- workflow result graph
- persistent run store

## v0.4 - Memory Engine

- SQLite memory adapter
- conversation/session indexing
- summarization hooks
- optional vector memory interface
- memory import/export

## v0.5 - Plugin Runtime

- JVM plugin loading
- tool plugin API
- agent plugin API
- memory plugin API
- plugin manifest validation

## v1.0 - Agent OS Developer Experience

- stable runtime API
- stable DSL
- CLI debugger
- process manager UI
- workflow graph visualizer
- production examples
