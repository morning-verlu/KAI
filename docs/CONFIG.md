# Project Config

KAI OS can run the built-in `planner -> executor -> validator` workflow, or load an editable project workflow from `kaios.json`.

```bash
kaios config templates
kaios init --template research
kaios config validate
kaios config show
kaios run "map the JVM agent runtime"
```

`kaios init` refuses to overwrite an existing file unless you pass `--force`:

```bash
kaios init --force
```

When `kaios.json` exists in the current directory, `kaios run "task"` uses it automatically. Use `--default` to force the built-in workflow:

```bash
kaios run --default "quick smoke test"
```

Use a different path with `--config`:

```bash
kaios init --template research --config workflows/research.json
kaios run --config workflows/research.json "analyze a release plan"
```

## Context Files

Attach files or directories from the current workspace when a run needs project context:

```bash
kaios run --context README.md "summarize this project"
kaios run --context README.md --context docs --out artifacts/project.md "explain the architecture"
```

Context is bounded and local by default:

- paths must stay inside the current working directory.
- generated/runtime directories such as `.git`, `.kaios`, `build`, `node_modules`, `out`, and `target` are skipped.
- only readable text files are loaded.
- the default total context limit is 80,000 characters. Override it with `KAIOS_CONTEXT_MAX_CHARS`.
- snapshots and Markdown artifacts store context source summaries rather than the raw context payload.

## Templates

List templates:

```bash
kaios config templates
```

Built-in templates:

- `default`: planner -> executor -> validator baseline workflow.
- `research`: researcher -> synthesizer -> validator for research and answers.
- `code-review`: inspector -> reviewer -> validator for code review style tasks.
- `release`: planner -> executor -> verifier -> announcer for release operations.

Generate a template:

```bash
kaios init --template code-review
```

## Shape

```json
{
  "name": "custom-research",
  "agents": [
    {
      "id": "researcher",
      "instruction": "Gather useful context for the task.",
      "tools": ["echo", "clock"],
      "dependsOn": [],
      "memory": true
    },
    {
      "id": "writer",
      "instruction": "Write a concise answer.",
      "tools": ["echo"],
      "dependsOn": ["researcher"],
      "memory": true
    },
    {
      "id": "validator",
      "instruction": "Check the answer and mark it accepted.",
      "tools": ["echo"],
      "dependsOn": ["writer"],
      "memory": true
    }
  ]
}
```

Fields:

- `name`: workflow name shown in snapshots, `kaios ps`, and `kaios inspect`.
- `agents`: ordered list of agent process nodes.
- `id`: unique agent process name.
- `instruction`: system-style guidance passed to the configured model provider.
- `tools`: syscall tools the agent may call.
- `dependsOn`: upstream agent ids that must complete before this agent is scheduled.
- `memory`: enables the configured memory store for that agent. Defaults to `true`.
- `fallback`: optional fallback agent id to run when this node fails.
- `fallbackOnly`: keeps a node out of the normal DAG and reserves it for fallback routing.

## Built-In Tools

The v0.1 safe syscall set is intentionally small:

- `echo`: returns a supplied message.
- `clock`: returns the current UTC timestamp.
- `mock-http`: returns a deterministic mocked HTTP response.
- `file`: reads, writes, lists, and checks files inside `.kaios/files`.

Tool names are validated before any agent process starts. Unknown tools fail fast.

## Validation

The CLI validates project configs before spawning agents. Run validation directly with:

```bash
kaios config validate
kaios config validate --config workflows/research.json
```

Validation checks:

- workflow name is not blank.
- at least one non-fallback agent exists.
- agent ids are non-blank and unique.
- every tool exists in the built-in registry.
- every dependency points to a known agent.
- every fallback points to a known agent and does not point to itself.
- dependency edges do not contain cycles.

## Show the DAG

Print the configured process graph before running it:

```bash
kaios config show
```

Example output:

```text
config: /path/to/kaios.json
workflow: research
agents:
  researcher tools=clock,echo,mock-http dependsOn=-
  synthesizer tools=echo dependsOn=researcher
  validator tools=echo dependsOn=synthesizer
graph:
  <input> -> researcher
  researcher -> synthesizer
  synthesizer -> validator
```

## Observability

Configured workflows use the same process observability as the built-in workflow:

```bash
kaios ps <run-id>
kaios inspect <run-id>
kaios report <run-id>
kaios export <run-id>
```

Snapshots are still written under `.kaios/runs/`, so custom workflows can be inspected later without re-running the task.

## Artifacts

Export the run as Markdown when you need a shareable handoff:

```bash
kaios run --out artifacts/runtime.md "map the JVM agent runtime"
kaios export <run-id>
kaios export <run-id> --out artifacts/run.md
```

Artifacts include the task, final output, process table, and lifecycle events. The default export path is `.kaios/artifacts/<run-id>.md`. Existing files are protected; use `kaios run --force-output --out ...` for run-time artifacts and `kaios export <run-id> --force` for exports.
