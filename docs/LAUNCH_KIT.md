# Launch Kit

Use this page when announcing KAI OS.

## One-Line Pitch

KAI OS is an AI Agent Operating System in Kotlin: agents run like processes, workflows schedule them like a kernel, and tools are syscall boundaries.

Launch site: https://morning-verlu.github.io/KAI/
Social preview image: https://morning-verlu.github.io/KAI/assets/kaios-social-card.png
Demo GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
Trust contract: https://github.com/morning-verlu/KAI/blob/main/docs/TRUST.md
Install page: https://github.com/morning-verlu/KAI/blob/main/docs/INSTALL.md

## Short Post

```text
I built KAI OS: an AI Agent Operating System in Kotlin.

Agent = Process
Workflow = Scheduler
Tool = Syscall

It runs planner -> executor -> validator as inspectable agent processes with PID, token usage, memory, syscalls, and lifecycle events.

No API key needed for the first demo.

It can also emit a KAI Process Trace (`kaios.process-trace/v1`) for CI, replay, audit, and future UI surfaces.
It can package a KAI Run Capsule (`kaios.run-capsule/v1`) with snapshot, trace, provenance hashes, and replay commands.
It can replay a shared capsule offline (`kaios.run-replay/v1`) by rebuilding the trace from the embedded snapshot.
It can diff two shared capsules offline (`kaios.run-diff/v1`) for stable regression checks.
It can generate a static Agent Process Manager report for screenshots.
The README now includes a terminal process-table preview for quick sharing.
There is a short CLI demo GIF for run -> ps -> inspect.
New users can run `kaios next` for one read-only recommendation, then `kaios quickstart` to complete demo, setup, verify, and evidence in one no-key pass before choosing their own task. Use `kaios quickstart --no-ci` for local-only onboarding without writing a GitHub Actions workflow.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
```

## Technical Post

```text
Most agent frameworks model AI work as prompts, chains, or chat sessions.

KAI OS models AI work as runtime infrastructure:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The v0.3 Evidence Core release is a runnable Kotlin/JVM seed:

- agent lifecycle
- coroutine DAG workflow scheduler
- observable scheduler retry policy
- permissioned syscall tools
- deterministic mock model provider
- OpenAI-compatible and Ollama providers
- scoped file syscall
- allowlisted real HTTP syscall
- real-provider `KAIOS_SYSCALL` directives
- SQLite memory adapter
- JSON run snapshots
- CLI process table
- KAI Process Trace schema
- KAI Run Capsule schema
- process recovery evidence with new PIDs
- priority scheduler evidence
- syscall audit ledger with denied calls, tool time, and estimated cost
- static Agent Process Manager report
- Markdown run artifacts for shareable handoff
- no-key Markdown and JSON workspace analysis reports
- Workspace Index with language stats, notable files, and source maps
- project-aware runs with `kaios context`, `.kaiosignore`, and bounded `--context` files and directories
- one-command KAI OS onboarding after installation
- kaios demo for a no-key first run with process table and trace artifact
- kaios next for one read-only workspace-aware recommendation
- kaios quickstart for one-command no-key onboarding with setup, CI gate, verify, evidence, and next actions
- kaios quickstart --no-ci for local-only onboarding when a project is not ready for CI files
- kaios review for current-change review with Markdown artifact, process trace, replayable capsule, and optional baseline gate
- kaios evidence --summary for PR-friendly Verdict, Changed Runtime Behavior, Fix First, and Process Table output
- kaios setup for one-command project workflow bootstrap
- kaios gate for one-command readiness, trace, evidence, replay, and optional baseline checks
- kaios verify for lower-level CI-compatible readiness control
- kaios capsule for portable run evidence packages
- kaios doctor for local environment diagnostics
- kaios init templates, config validation, and editable agent DAGs
- kaios doctor --json for machine-readable environment diagnostics
- kaios runs --json for a stable local run registry
- kaios config validate --json for CI-safe workflow validation
- kaios init --ci for a ready-to-commit GitHub Actions Agent Gate powered by `kaios verify --evidence`
- kaios bug-report for safe GitHub issue diagnostics and support handoff

Try KAI OS:

kaios tour
kaios next
kaios quickstart --dry-run
kaios quickstart
kaios quickstart --no-ci
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios runs --json
kaios ps
kaios trace --check
kaios gate --baseline artifacts/baseline.capsule.json --check
kaios export

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
Release ZIP: https://github.com/morning-verlu/KAI/releases/download/v0.3.1/kaios-0.3.1.zip
Installer: curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
Install options after the product demo: Homebrew, hosted installer, release ZIP, or source build
```

## Show HN Draft

```text
Show HN: KAI OS – AI Agent Operating System in Kotlin

I am building KAI OS, a Kotlin/JVM runtime that treats AI agents like operating-system processes.

The core metaphor:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current v0.3 demo runs a planner -> executor -> validator workflow and lets you inspect each agent process with PID, token usage, context size, syscall count, tool time, estimated cost, denied syscall count, duration, lifecycle events, optional Workspace Index source maps, project context sources, retry attempts, recovery lineage, scheduler evidence, a reusable `kaios.process-trace/v1` trace, and a portable `kaios.run-capsule/v1` evidence package that can be validated, replayed, or diffed from shared JSON files. The Evidence OS path is `kaios review`: it turns the current Git change set into a Markdown review artifact, process trace, replayable capsule, and optional baseline gate. You can generate no-key Markdown or JSON project reports with `kaios analyze`, preview project shape with `kaios index`, preview context with `kaios context`, exclude local noise with `.kaiosignore`, opt into real HTTP syscalls with `KAIOS_HTTP_ALLOWLIST`, and let real providers request tools through `KAIOS_SYSCALL` directives.

It uses a deterministic mock model provider, so no API key is needed.

I am interested in feedback from Kotlin/JVM developers, agent-framework builders, and people thinking about AI runtime infrastructure.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```

## Reddit / Kotlin Slack Draft

```text
I started KAI OS, a Kotlin/JVM agent runtime built around an OS metaphor:

Agent = Process
Workflow = Scheduler
Tool = Syscall

The first Evidence Core release is intentionally small but runnable: a default planner -> executor -> validator workflow, current-change review through `kaios review`, process metrics, lifecycle events, process recovery evidence, priority scheduler evidence, syscall audit ledger, KAI Process Trace JSON, KAI Run Capsule JSON, offline capsule replay/diff, observable retries, permissioned tools, no-key Markdown and JSON workspace analysis reports, Workspace Index source maps, previewable bounded project context, allowlisted HTTP, and JSON run snapshots.

I would love feedback on the Kotlin API/DSL and runtime model.

https://github.com/morning-verlu/KAI
https://morning-verlu.github.io/KAI/
```

## First Demo Flow

KAI OS product flow. Lead with what KAI OS does; installation is a separate adoption step:

```bash
kaios tour
kaios next
kaios quickstart --dry-run
kaios quickstart
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios ps
kaios inspect
kaios trace --check
kaios gate --baseline artifacts/baseline.capsule.json --check
kaios report
```

Hosted installer:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
kaios quickstart
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios ps
kaios inspect
kaios trace --check
kaios gate --baseline artifacts/baseline.capsule.json --check
kaios report
```

Download ZIP:

```bash
curl -L -o kaios-0.3.1.zip https://github.com/morning-verlu/KAI/releases/download/v0.3.1/kaios-0.3.1.zip
unzip kaios-0.3.1.zip
./kaios-0.3.1/bin/kaios tour
./kaios-0.3.1/bin/kaios quickstart
./kaios-0.3.1/bin/kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
./kaios-0.3.1/bin/kaios gate --baseline artifacts/baseline.capsule.json --check
```

Build from source:

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios tour
build/install/kaios-cli/bin/kaios quickstart
build/install/kaios-cli/bin/kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
build/install/kaios-cli/bin/kaios ps
build/install/kaios-cli/bin/kaios inspect
build/install/kaios-cli/bin/kaios trace --check
build/install/kaios-cli/bin/kaios gate --baseline artifacts/baseline.capsule.json --check
build/install/kaios-cli/bin/kaios report
```

## Launch Checklist

- Pin the repository on the GitHub profile.
- Share the GitHub Pages launch site alongside the repository.
- Add the repo link to social bios for launch week.
- Post the short pitch first, then follow with the technical post.
- Reply quickly to the first issues and comments.
- Ship one follow-up commit within 24 hours of launch.
- Create issues for model providers, safe tools, scheduler, memory, and UI.
- Add a short demo GIF or terminal screenshot as soon as possible. (README terminal preview shipped.)
