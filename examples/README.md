# Examples

These examples use the deterministic mock model provider, so no API key is required.

## Run the Default Workflow

```bash
./gradlew installDist
build/install/kaios-cli/bin/kaios doctor
build/install/kaios-cli/bin/kaios run "analyze crypto market"
```

The default workflow is:

```text
planner -> executor -> validator
```

Each node becomes an agent process. The CLI writes a JSON snapshot under `.kaios/runs/`.

## Create a Project Workflow Config

Generate an editable `kaios.json`:

```bash
build/install/kaios-cli/bin/kaios config templates
build/install/kaios-cli/bin/kaios init --template research
build/install/kaios-cli/bin/kaios config validate
build/install/kaios-cli/bin/kaios config show
```

Run the configured workflow. When `kaios.json` exists in the current directory, `kaios run` uses it automatically:

```bash
build/install/kaios-cli/bin/kaios run "map the JVM agent runtime"
```

Write the final output and process table to a Markdown artifact during the run:

```bash
build/install/kaios-cli/bin/kaios run --out artifacts/runtime.md "map the JVM agent runtime"
```

Attach local context files or directories when the workflow should reason over project material:

```bash
build/install/kaios-cli/bin/kaios run --context README.md --context docs --out artifacts/project.md "summarize this project"
```

The run snapshot and Markdown artifact include a source summary such as `README.md` and `docs/CONFIG.md`, while the saved context metadata avoids copying the full payload.

The generated config starts with the default process graph:

```json
{
  "name": "default",
  "agents": [
    {
      "id": "planner",
      "instruction": "Plan the task as an agent process.",
      "tools": ["echo", "clock"],
      "dependsOn": []
    },
    {
      "id": "executor",
      "instruction": "Execute the plan through permitted syscalls.",
      "tools": ["echo", "mock-http"],
      "dependsOn": ["planner"]
    },
    {
      "id": "validator",
      "instruction": "Validate the executor output.",
      "tools": ["echo"],
      "dependsOn": ["executor"]
    }
  ]
}
```

Change the agent ids, instructions, tools, and `dependsOn` edges to shape your own DAG. The CLI validates unknown tools, unknown dependencies, duplicate agents, and dependency cycles before it spawns any agent process.

Use a specific config file when you have more than one workflow:

```bash
build/install/kaios-cli/bin/kaios init --template release --config workflows/release.json
build/install/kaios-cli/bin/kaios run --config workflows/release.json "prepare v0.2.0"
```

Force the built-in default workflow even when `kaios.json` exists:

```bash
build/install/kaios-cli/bin/kaios run --default "quick smoke test"
```

## Inspect Processes

```bash
build/install/kaios-cli/bin/kaios ps <run-id>
```

The process table shows:

- PID
- agent name
- lifecycle state
- token usage
- context size
- syscall count
- duration

## Inspect Events

```bash
build/install/kaios-cli/bin/kaios inspect <run-id>
```

The event log shows process lifecycle transitions and syscall activity.

## Generate a Process Manager Report

```bash
build/install/kaios-cli/bin/kaios report <run-id>
```

The report is a standalone HTML file under `.kaios/reports/` with a run list, process table, workflow graph, lifecycle timeline, and final output. It is intended for screenshots and quick visual debugging.

## Export a Markdown Artifact

```bash
build/install/kaios-cli/bin/kaios export <run-id>
build/install/kaios-cli/bin/kaios export <run-id> --out artifacts/run.md
```

Artifacts are Markdown files with the task, final output, process table, and lifecycle events. The default location is `.kaios/artifacts/<run-id>.md`. Existing files are protected; use `--force-output` with `run --out` and `--force` with `export`.

## Try Other Tasks

```bash
build/install/kaios-cli/bin/kaios run "draft a release plan"
build/install/kaios-cli/bin/kaios run "summarize JVM agent infrastructure"
build/install/kaios-cli/bin/kaios run "design a safe file tool"
```

## Use a Real Provider

OpenAI-compatible endpoint:

```bash
export KAIOS_MODEL_PROVIDER=openai
export OPENAI_API_KEY="..."
export OPENAI_MODEL="your-model"
build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

Ollama:

```bash
export KAIOS_MODEL_PROVIDER=ollama
export OLLAMA_MODEL="your-local-model"
build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

See [../docs/PROVIDERS.md](../docs/PROVIDERS.md) for provider details.

## Use SQLite Memory

```bash
export KAIOS_MEMORY_STORE=sqlite
export KAIOS_SQLITE_PATH=".kaios/kaios.db"
build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

See [../docs/MEMORY.md](../docs/MEMORY.md) for memory store details.

## Scoped File Syscall

The built-in `file` tool supports `read`, `write`, `list`, and `exists` operations inside a configured root. The default root is `.kaios/files`, and traversal outside that root is rejected.

Kotlin DSL example:

```kotlin
val writer = agent("writer") {
    tool("file")
}
```

Tool call shape:

```kotlin
ToolCall(
    "file",
    mapOf(
        "op" to "write",
        "path" to "notes/plan.txt",
        "content" to "agent file syscall"
    )
)
```
