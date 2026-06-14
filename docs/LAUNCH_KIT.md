# Launch Kit

Use this page when announcing KAI OS.

## One-Line Pitch

KAI OS is an AI Agent Operating System in Kotlin: agents run like processes, workflows schedule them like a kernel, and tools are syscall boundaries.

Launch site: https://morning-verlu.github.io/KAI/
Social preview image: https://morning-verlu.github.io/KAI/assets/kaios-social-card.png
Demo GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
Installer: https://morning-verlu.github.io/KAI/install.sh
Homebrew: brew tap morning-verlu/tap && brew install kaios

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
New users can run `kaios demo` before choosing their own task.

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

The v0.1 release is a runnable Kotlin/JVM seed:

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
- static Agent Process Manager report
- Markdown run artifacts for shareable handoff
- no-key Markdown and JSON workspace analysis reports
- Workspace Index with language stats, notable files, and source maps
- project-aware runs with `kaios context`, `.kaiosignore`, and bounded `--context` files and directories
- install-first onboarding through Homebrew or the checksum-verifying installer
- kaios demo for a no-key first run with process table and trace artifact
- kaios setup for one-command project workflow bootstrap
- kaios verify for one-command readiness and evidence gates in local projects and CI
- kaios capsule for portable run evidence packages
- kaios doctor for local environment diagnostics
- kaios init templates, config validation, and editable agent DAGs
- kaios doctor --json for machine-readable environment diagnostics
- kaios runs --json for a stable local run registry
- kaios config validate --json for CI-safe workflow validation
- kaios init --ci for a ready-to-commit GitHub Actions Agent Gate powered by `kaios verify --evidence`
- kaios bug-report for safe GitHub issue diagnostics and support handoff

Try:

brew tap morning-verlu/tap
brew install kaios
kaios demo
kaios setup --ci
kaios verify --evidence --force
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios runs --json
kaios ps latest
kaios trace latest
kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
kaios export latest

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
Release ZIP: https://github.com/morning-verlu/KAI/releases/download/v0.1.69/kaios-0.1.69.zip
Installer: curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
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

The current v0.1 demo runs a planner -> executor -> validator workflow and lets you inspect each agent process with PID, token usage, context size, syscall count, duration, lifecycle events, optional Workspace Index source maps, project context sources, retry attempts, a reusable `kaios.process-trace/v1` trace, and a portable `kaios.run-capsule/v1` evidence package that can be validated, replayed, or diffed from shared JSON files. You can generate no-key Markdown or JSON project reports with `kaios analyze`, preview project shape with `kaios index`, preview context with `kaios context`, exclude local noise with `.kaiosignore`, opt into real HTTP syscalls with `KAIOS_HTTP_ALLOWLIST`, and let real providers request tools through `KAIOS_SYSCALL` directives.

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

The first version is intentionally small but runnable: a default planner -> executor -> validator workflow, process metrics, lifecycle events, KAI Process Trace JSON, KAI Run Capsule JSON, offline capsule replay/diff, observable retries, permissioned tools, no-key Markdown and JSON workspace analysis reports, Workspace Index source maps, previewable bounded project context, allowlisted HTTP, and JSON run snapshots.

I would love feedback on the Kotlin API/DSL and runtime model.

https://github.com/morning-verlu/KAI
https://morning-verlu.github.io/KAI/
```

## First Demo Flow

Install-first:

```bash
brew tap morning-verlu/tap
brew install kaios
kaios demo
kaios setup --ci
kaios verify --evidence --force
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios ps latest
kaios inspect latest
kaios trace latest
kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
kaios report latest
```

Hosted installer:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios demo
kaios setup --ci
kaios verify --evidence --force
kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
kaios ps latest
kaios inspect latest
kaios trace latest
kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
kaios report latest
```

Download ZIP:

```bash
curl -L -o kaios-0.1.69.zip https://github.com/morning-verlu/KAI/releases/download/v0.1.69/kaios-0.1.69.zip
unzip kaios-0.1.69.zip
./kaios-0.1.69/bin/kaios demo
./kaios-0.1.69/bin/kaios setup --ci
./kaios-0.1.69/bin/kaios verify --evidence --force
./kaios-0.1.69/bin/kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
./kaios-0.1.69/bin/kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
```

Build from source:

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios demo
build/install/kaios-cli/bin/kaios setup --ci
build/install/kaios-cli/bin/kaios verify --evidence --force
build/install/kaios-cli/bin/kaios run --index . --context README.md --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"
build/install/kaios-cli/bin/kaios ps latest
build/install/kaios-cli/bin/kaios inspect latest
build/install/kaios-cli/bin/kaios trace latest
build/install/kaios-cli/bin/kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
build/install/kaios-cli/bin/kaios report latest
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
