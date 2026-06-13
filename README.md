# KAI OS

> AI Agent Operating System in Kotlin.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg)](https://kotlinlang.org/)

KAI OS is a Kotlin runtime for orchestrating AI agents like operating-system processes.

It is not a chatbot framework, not a LangChain clone, and not just a CLI. The goal is a developer-native runtime where agents have lifecycle, memory, permissions, metrics, and syscall-style tool boundaries.

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
```

## Why This Exists

Most agent frameworks model AI work as chains, prompts, or chat sessions. KAI OS models AI work as runtime infrastructure:

- Spawn agents as isolated processes.
- Track token usage like CPU.
- Track context size like memory.
- Track tool calls like IO/syscalls.
- Schedule multi-agent workflows as DAGs.
- Persist run snapshots for inspection and debugging.

Kotlin gives this model a strong foundation: JVM ecosystem reach, type safety, coroutines-ready concurrency, DSL ergonomics, and a path toward Kotlin Multiplatform.

## Quick Start

```bash
./gradlew test installDist
build/install/kaios-cli/bin/kaios run "analyze crypto market"
```

Example output:

```text
run_id: run-97381ae9
success: true
snapshot: .kaios/runs/run-97381ae9.json

validate:350c4677 accepted result from after executor
syscall echo: validated:350c4677
```

Inspect the agent process table:

```bash
build/install/kaios-cli/bin/kaios ps run-97381ae9
```

```text
RUN run-97381ae9  workflow=default  success=true
PID     AGENT         STATE       TOKENS    MEMORY    SYSCALLS  DURATION
1       planner       SUCCEEDED   13        137b      1         10ms
2       executor      SUCCEEDED   24        282b      1         0ms
3       validator     SUCCEEDED   27        273b      1         0ms
```

Inspect lifecycle events:

```bash
build/install/kaios-cli/bin/kaios inspect run-97381ae9
```

## Architecture

```text
                CLI / API / UI
                      |
                Agent Runtime
          lifecycle | memory | context
                      |
      +---------------+---------------+
      |               |               |
  Scheduler       Tool System     Memory Layer
  DAG engine      syscalls        run state
```

Modules:

- `runtime-core`: process lifecycle, scheduler, events, model abstraction, DSL.
- `tool-runtime`: built-in syscall tools.
- `memory-engine`: in-memory session memory and JSON run snapshots.
- `model-providers`: OpenAI-compatible and Ollama model provider implementations.
- `kaios-cli`: `kaios run`, `kaios ps`, and `kaios inspect`.

Read the deeper design notes in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Kotlin DSL

```kotlin
val planner = agent("planner") {
    instruction("Plan the task as an agent process.")
    tool("echo")
    tool("clock")
    memory(sessionMemory)
}

val defaultWorkflow = workflow("default") {
    node("planner", planner)
    node("executor", agent("executor") { tool("mock-http") }).dependsOn("planner")
    node("validator", agent("validator") { tool("echo") }).dependsOn("executor")
}
```

See [examples/README.md](examples/README.md) for runnable CLI examples and the current v0.1 behavior.

For launch posts, demos, and community announcements, see [docs/LAUNCH_KIT.md](docs/LAUNCH_KIT.md).

For real model execution, see [docs/PROVIDERS.md](docs/PROVIDERS.md).

## Current Status

KAI OS is early v0.1 infrastructure. Today it includes:

- Deterministic `MockModelProvider`, no API key needed.
- OpenAI-compatible and Ollama providers for real model execution.
- Agent lifecycle: spawn, start, suspend, resume, cancel, succeed, fail.
- Process metrics: PID, state, token usage, context size, syscall count, duration.
- Simple DAG scheduler with parallel-ready nodes and fallback routing.
- Permissioned tools: `echo`, `clock`, `mock-http`.
- Session memory and JSON snapshots under `.kaios/runs/`.
- CLI process table and run inspector.

Next milestones are tracked in [ROADMAP.md](ROADMAP.md).

## Development

Requirements:

- Java 17+
- No global Gradle install required

Commands:

```bash
./gradlew clean test installDist
build/install/kaios-cli/bin/kaios run "draft a launch plan"
scripts/demo.sh "analyze crypto market"
```

## Contributing

KAI OS is designed for people who want agent infrastructure to feel like systems programming again. Contributions are welcome around runtime design, scheduler behavior, tool isolation, model providers, memory adapters, and the future visual process manager.

Start with [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache-2.0. See [LICENSE](LICENSE).
