# Workspace Index

Workspace Index gives KAI OS a cheap project map before an agent run.

Use it when you want the runtime to understand repository shape without loading every file into context.

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios run --index . --context README.md --out artifacts/project.md --force "summarize this project"
```

## Analyze vs Index vs Context

`kaios analyze` is a deterministic Markdown or JSON report:

- project summary
- stack signals
- language and directory tables
- notable files and hotspots
- test and quality signals
- recommended action plan with priorities, commands, and reasons
- suggested KAI OS commands

It does not require a model provider or API key.

Use JSON when CI, dashboards, or other tools need a stable schema:

```bash
kaios analyze . --format json --out artifacts/analysis.json --force
```

`kaios index` is a source map:

- language distribution
- file, line, and byte counts
- top directories
- notable files such as README, Gradle files, docs, source, and tests
- largest readable text files

`kaios context` is bounded file content:

- selected files or directories
- readable text only
- character limits
- source summaries in snapshots and artifacts

Use both when a task needs orientation and evidence:

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios analyze . --format json --out artifacts/analysis.json --force
kaios index .
kaios context README.md docs
kaios run --index . --context README.md --context docs "explain the architecture"
```

## Ignore Rules

Workspace Index uses the same safety rules as context loading.

Generated and runtime directories such as `.git`, `.kaios`, `artifacts`, `build`, `node_modules`, `out`, and `target` are skipped by default. Add `.kaiosignore` for project-specific exclusions:

```gitignore
secrets/
*.local.md
tmp/
```

## Limits

The default index scans up to 500 readable text files. Override this for larger repositories:

```bash
KAIOS_INDEX_MAX_FILES=1000 kaios index .
```

Workspace Index records metadata, not full file contents. Use `--context` for the exact files an agent should read.

## Artifact Behavior

When `--index` is used during a run, snapshots and Markdown artifacts include a compact `Workspace Index` summary:

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios analyze . --format json --out artifacts/analysis.json --force
kaios run --index . --out artifacts/project.md --force "summarize this project"
kaios export
```

This keeps handoff artifacts useful without copying the repository into every report.
