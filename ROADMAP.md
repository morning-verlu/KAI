# Roadmap

KAI OS is currently v0.3.1: a local-first Evidence OS for AI agents in Kotlin. The product wedge is not "more agents"; it is turning agent work into process traces, replayable capsules, syscall ledgers, and CI-grade proof.

## Shipped

### Runtime Seed

- Kotlin/JVM multi-module runtime with deterministic `MockModelProvider`.
- Agent process lifecycle: spawn, start, suspend, resume, cancel, succeed, fail.
- Process metrics: PID, state, tokens, context size, syscalls, duration, timestamps.
- DAG workflow scheduler, session memory, SQLite memory adapter, and JSON run snapshots.
- Permissioned built-in syscalls: `echo`, `clock`, `mock-http`, allowlisted `http`, scoped `file`.
- CLI inspection path: `run`, `ps`, `inspect`, `trace`, `capsule`, `replay`, `diff`, `evidence`.

### Evidence OS Path

- `kaios quickstart` for no-key onboarding, project config, verify, evidence, and next actions.
- `kaios review` for dirty Git workspaces: Markdown review artifact, process trace, replayable capsule, and `kaios.review/v1` JSON.
- `kaios evidence --summary` for PR-friendly proof and `--baseline --check` for behavioral gates.
- Workspace Index, bounded context loading, `.kaiosignore`, project analysis, and safe bug reports.
- Hosted installer and first-run `kaios tour` that runs the Evidence OS loop in a disposable repo.

### Evidence Core

- Process recovery evidence: crash events, failure kinds, memory isolation, recovery lineage, and `kaios recover --dry-run`.
- Priority scheduler evidence: deterministic priority ordering, event-triggered nodes, retry, recovery, fallback, timeout, sibling cancellation, and local worker backend records.
- Syscall ledger: capability grants, allowed/denied audit records, redacted arguments, tool duration, estimated cost fields, and denied syscall counters.
- Backward-compatible JSON contract fields in traces, capsules, review JSON, and evidence output.

## Next: v0.4 Evidence CI

- GitHub Actions examples that publish `kaios evidence --summary` as PR-visible Markdown.
- Stable baseline workflow: create, update, compare, and explain baseline capsules.
- Sample capsules and traces that new users can inspect without running a project first.
- Release-quality docs for `kaios review`, `kaios evidence`, `kaios recover`, and JSON contracts.
- More contract tests around dirty workspaces, ignored files, binary files, and baseline drift.

## v0.5 Runtime Extensions

- Provider function-calling polish for OpenAI-compatible and Ollama execution.
- Narrow plugin API for tools and memory adapters, only where it strengthens evidence.
- Richer memory scopes and import/export flows.
- More runnable JVM/Kotlin project examples for maintainers and backend teams.

## v1.0 Agent OS Developer Experience

- Stable runtime API and stable CLI JSON contracts.
- Agent process manager UI for traces, capsules, syscalls, recovery, and cost.
- Workflow graph visualizer for scheduler evidence.
- Production examples that show KAI OS as the evidence layer beside real providers.
