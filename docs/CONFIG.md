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

If a task itself starts with `-`, separate CLI options from task text with `--`:

```bash
kaios run -- "--audit release flags"
```

## Workspace Index

Build a no-key project report and source map before a run:

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios analyze . --format json --out artifacts/analysis.json --force
kaios index .
kaios run --index . "summarize the project shape"
kaios run --index . --config workflows/research.json "map the runtime architecture"
```

`kaios analyze` writes a deterministic Markdown or JSON report with stack signals, architecture map, hotspots, quality signals, and suggested next commands. `kaios index` reports language distribution, top directories, notable files, line counts, and largest readable text files. Both use the same workspace boundary and `.kaiosignore` rules as context loading.

Use Workspace Index for orientation and `--context` for specific file evidence:

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios analyze . --format json --out artifacts/analysis.json --force
kaios run --index . --context README.md --context docs "explain the architecture"
```

## Context Files

Attach files or directories from the current workspace when a run needs project context:

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios analyze . --format json --out artifacts/analysis.json --force
kaios index .
kaios context .
kaios context README.md docs
kaios run --context README.md "summarize this project"
kaios run --index . --context README.md --context docs --out artifacts/project.md --force "explain the architecture"
```

`kaios context` previews the exact bounded source set before a run:

```text
CONTEXT
root: /path/to/project
files: 2
chars: 12842/80000
truncated: false

PATH                          CHARS     ORIGINAL    STATUS
README.md                     8139      8139        loaded
docs/CONFIG.md                4703      4703        loaded
```

Context is bounded and local by default:

- paths must stay inside the current working directory.
- generated/runtime directories such as `.git`, `.kaios`, `artifacts`, `build`, `node_modules`, `out`, and `target` are skipped.
- only readable text files are loaded.
- the default total context limit is 80,000 characters. Override it with `KAIOS_CONTEXT_MAX_CHARS`.
- snapshots and Markdown artifacts store context source summaries rather than the raw context payload.

Add `.kaiosignore` at the workspace root to exclude extra paths. It supports comments, `*` and `?` globs, directory rules with trailing `/`, and `!` negation:

```gitignore
# never send local secrets into agent context
secrets/
*.local.md
!docs/public.local.md
```

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
      "retries": 1,
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
- `retries`: additional attempts for transient node failures. Defaults to `0`, maximum `10`.
- `memory`: enables the configured memory store for that agent. Defaults to `true`.
- `fallback`: optional fallback agent id to run when this node fails.
- `fallbackOnly`: keeps a node out of the normal DAG and reserves it for fallback routing.

Retries are observable. Every attempt spawns its own agent process, failed attempts remain visible in `kaios ps`, and the event log records `RETRYING` before the next attempt starts.

## Built-In Tools

The v0.1 safe syscall set is intentionally small:

- `echo`: returns a supplied message.
- `clock`: returns the current UTC timestamp.
- `mock-http`: returns a deterministic mocked HTTP response.
- `http`: performs real allowlisted HTTP `GET`, `HEAD`, and `POST` requests when `KAIOS_HTTP_ALLOWLIST` is set.
- `file`: reads, writes, lists, and checks files inside `.kaios/files`.

Tool names are validated before any agent process starts. Unknown tools fail fast.

See [TOOLS.md](TOOLS.md) for HTTP allowlist rules, file syscall scope, and tool examples.

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
- every retry count is between `0` and `10`.
- dependency edges do not contain cycles.

## Show the DAG

Print the configured process graph before running it:

```bash
kaios config show
```

Example output:

```text
config: /path/to/kaios.json
workflow: custom-research
agents:
  researcher tools=clock,echo dependsOn=- retries=1
  writer tools=echo dependsOn=researcher
  validator tools=echo dependsOn=writer
graph:
  <input> -> researcher
  researcher -> writer
  writer -> validator
```

## Observability

Configured workflows use the same process observability as the built-in workflow:

```bash
kaios run --config kaios.json --trace-out artifacts/trace.json --force "review this release"
kaios ps latest
kaios inspect latest
kaios trace latest
kaios trace latest --json
kaios trace latest --json --out artifacts/trace.json --force
kaios report latest
kaios export latest
```

Snapshots are still written under `.kaios/runs/`, so custom workflows can be inspected later without re-running the task. `kaios trace` renders the saved run as `kaios.process-trace/v1`, a stable text/JSON view for CI checks, visualizers, replay tooling, and audit trails.

## Artifacts

Export the run as Markdown when you need a shareable handoff:

```bash
kaios run --out artifacts/runtime.md "map the JVM agent runtime"
kaios export latest
kaios export latest --out artifacts/run.md
```

Artifacts include the task, final output, process table, and lifecycle events. The default export path is `.kaios/artifacts/<run-id>.md`. Existing files are protected; use `kaios run --force --out ...` for run-time artifacts and `kaios export latest --force` for exports.
