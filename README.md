# KAI OS

> AI Agent Operating System in Kotlin.

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg)](https://kotlinlang.org/)

Website: [morning-verlu.github.io/KAI](https://morning-verlu.github.io/KAI/)

KAI OS is a Kotlin runtime for orchestrating AI agents like operating-system processes.

It is not a chatbot framework, not a LangChain clone, and not just a CLI. The goal is a developer-native runtime where agents have lifecycle, memory, permissions, metrics, and syscall-style tool boundaries.

![KAI OS CLI demo](docs/assets/kaios-demo.gif)

![KAI OS process table preview](docs/assets/kaios-process-table.svg)

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

Install with Homebrew:

```bash
brew tap morning-verlu/tap
brew install kaios
kaios doctor
kaios run "analyze crypto market"
```

Create a local workflow config when you want your own agent process graph:

```bash
kaios init --template research
kaios config show
kaios run --out artifacts/market.md "analyze crypto market"
```

Run against local project files when you want the agents to see real context:

```bash
kaios run --context README.md --out artifacts/project.md "summarize this project"
```

Or install with the hosted script:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios doctor
kaios run "analyze crypto market"
```

Or build from source:

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
kaios ps run-97381ae9
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
kaios inspect run-97381ae9
```

Generate a standalone Agent Process Manager report:

```bash
kaios report run-97381ae9
```

Export a Markdown artifact:

```bash
kaios export run-97381ae9
```

Attach local context files or directories:

```bash
kaios run --context README.md --context docs "explain the architecture"
```

KAI OS reads text files inside the current workspace, skips generated/runtime directories such as `.git`, `.kaios`, `build`, and `node_modules`, enforces size limits, and records a source summary in snapshots and artifacts.

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
- `kaios-cli`: `kaios init`, `kaios run`, `kaios runs`, `kaios ps`, `kaios inspect`, `kaios report`, context-file loading, and `kaios doctor`.

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

## Project Config

Use `kaios init` to generate `kaios.json`, then edit the agent DAG without recompiling Kotlin. Built-in templates include `default`, `research`, `code-review`, and `release`.

```json
{
  "name": "custom-research",
  "agents": [
    {
      "id": "researcher",
      "instruction": "Gather useful context for the task.",
      "tools": ["echo", "clock"]
    },
    {
      "id": "writer",
      "instruction": "Write a concise answer.",
      "tools": ["echo"],
      "dependsOn": ["researcher"]
    },
    {
      "id": "validator",
      "instruction": "Check the answer and mark it accepted.",
      "tools": ["echo"],
      "dependsOn": ["writer"]
    }
  ]
}
```

Run it with:

```bash
kaios config validate
kaios config show
kaios run "map the JVM agent runtime"
```

When `kaios.json` exists in the current directory, `kaios run "task"` uses it automatically. Use `kaios run --default "task"` to force the built-in workflow, or `kaios run --config path/to/workflow.json "task"` for a specific file.

See [docs/CONFIG.md](docs/CONFIG.md) for templates, config fields, validation rules, built-in tools, and fallback routing.

For launch posts, demos, and community announcements, see [docs/LAUNCH_KIT.md](docs/LAUNCH_KIT.md).

For real model execution, see [docs/PROVIDERS.md](docs/PROVIDERS.md).

For persisted memory, see [docs/MEMORY.md](docs/MEMORY.md).

For all install options, see [docs/INSTALL.md](docs/INSTALL.md).

## Current Status

KAI OS is early v0.1 infrastructure. Today it includes:

- Deterministic `MockModelProvider`, no API key needed.
- OpenAI-compatible and Ollama providers for real model execution.
- Agent lifecycle: spawn, start, suspend, resume, cancel, succeed, fail.
- Process metrics: PID, state, token usage, context size, syscall count, duration.
- Coroutine-based DAG scheduler with parallel-ready nodes, fallback routing, timeout policy, and sibling cancellation.
- Permissioned tools: `echo`, `clock`, `mock-http`, scoped `file`.
- Project workflow templates, config validation, config graph display, and auto-detected `kaios.json` runs.
- Project-aware runs with `kaios run --context <file-or-dir>` and bounded text ingestion.
- Session memory and JSON snapshots under `.kaios/runs/`.
- SQLite memory adapter for persisted agent process memory.
- CLI process table and run inspector.
- Markdown run artifacts with `kaios run --out` and `kaios export`.
- `kaios doctor` environment diagnostics for Java, provider, memory, snapshots, and writable runtime directories.
- Static Agent Process Manager HTML reports under `.kaios/reports/`.
- README-ready terminal process preview for launch sharing.
- CLI demo GIF for README, launch site, and social posts.

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
