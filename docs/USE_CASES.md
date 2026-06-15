# KAI OS Use Cases

KAI OS is useful when agent work needs runtime boundaries, process visibility, and portable evidence.

It is not trying to be the shortest path to a chatbot. It is the path for teams that want agent workflows to behave like inspectable software systems.

## 1. Add A No-Key Agent Gate To CI

Use this when a repository needs a deterministic agent readiness check before anyone wires real model credentials.

```bash
kaios setup --ci
kaios gate --config kaios.json
git add kaios.json .github/workflows/kaios.yml
```

What KAI OS gives you:

- a validated agent workflow config.
- a GitHub Actions Agent Gate that runs without API keys.
- `artifacts/kaios-verify.json` for machines.
- `artifacts/kaios-run.capsule.json` for replayable evidence.
- `artifacts/kaios-bug-report.json` when the gate fails.
- a PR-visible summary with Verdict, Why It Failed, Fix First, process metrics, trace status, and baseline drift when configured.

Why it matters:

You can introduce agent infrastructure into a repo without asking contributors for API keys, hidden setup, or local state.

## 2. Debug A Multi-Agent Run Like A Process Tree

Use this when an agent workflow fails or behaves differently across machines.

```bash
kaios quickstart
kaios ps
kaios inspect
kaios trace --check
```

What KAI OS gives you:

- process ids for each agent execution.
- lifecycle state for planner, executor, validator, and custom agents.
- token, context, syscall, and duration metrics.
- final output plus lifecycle events.
- a `kaios.process-trace/v1` contract that can be checked locally or in CI.

Why it matters:

Instead of asking "what did the agent do?", you can inspect which process ran, what it called, how much context it used, and where the workflow path went.

## 3. Package A Reproducible Run For Review Or Support

Use this when a run needs to be shared, audited, replayed, or compared later.

```bash
kaios evidence --out artifacts/run.capsule.json --force
kaios replay --file artifacts/run.capsule.json
kaios bug-report --out artifacts/kaios-bug-report.md --force
```

Add a baseline when you want stable regression checks:

```bash
kaios evidence --out artifacts/current.capsule.json --baseline artifacts/baseline.capsule.json --check --force
```

What KAI OS gives you:

- a portable KAI Run Capsule with snapshot, trace, and provenance hashes.
- offline replay without the original `.kaios/runs` directory.
- stable diffs that ignore run ids, timestamps, and duration noise.
- a safe support report with doctor checks, config validation, latest run summary, trace status, and a Fix First command.

Why it matters:

Agent output is easier to trust when the runtime state around it can be packaged and replayed.

## 4. Turn A Repository Into Bounded Agent Context

Use this when an agent needs repository awareness without dumping the whole workspace into a prompt.

```bash
kaios analyze . --out artifacts/analysis.md --force
kaios index .
kaios context README.md docs
kaios run --index . --context README.md --context docs --out artifacts/project.md --force "explain this project"
```

What KAI OS gives you:

- deterministic workspace analysis with stack, directory, file, test, and quality signals.
- a compact Workspace Index for repository shape.
- bounded context loading for selected files or directories.
- snapshots and Markdown artifacts that record source summaries without copying private or generated noise by default.

Why it matters:

You can keep agent context explicit and reviewable before moving to real providers.

## Choosing The First Command

When you are unsure where the workspace is, ask KAI OS:

```bash
kaios next
```

`kaios next` is read-only. It checks diagnostics, config validity, Git working tree changes, latest run evidence, and trace status, then prints one prioritized command:

- repair first when config or diagnostics are broken.
- analyze current changes before gates when the Git working tree is dirty.
- run the Agent Gate when evidence is missing.
- inspect processes when the workspace is healthy.

## Production Posture In v0.1

KAI OS v0.1 is deliberately small. The production value is not "many agents" or "many integrations"; it is a stable runtime surface:

- deterministic default provider.
- explicit tool permissions.
- validated project configs.
- inspectable process state.
- JSON contracts for CI and tooling.
- portable evidence for review, replay, support, and future UI surfaces.
