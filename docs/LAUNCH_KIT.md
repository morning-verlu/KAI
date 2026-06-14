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

It can also generate a static Agent Process Manager report for screenshots.
The README now includes a terminal process-table preview for quick sharing.
There is a short CLI demo GIF for run -> ps -> inspect.
New users can run `kaios doctor` before the first workflow.

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
- permissioned syscall tools
- deterministic mock model provider
- OpenAI-compatible and Ollama providers
- scoped file syscall
- SQLite memory adapter
- JSON run snapshots
- CLI process table
- static Agent Process Manager report
- Markdown run artifacts for shareable handoff
- project-aware runs with bounded `--context` files and directories
- install-first onboarding through Homebrew or the checksum-verifying installer
- kaios doctor for local environment diagnostics
- kaios init templates, config validation, and editable agent DAGs

Try:

brew tap morning-verlu/tap
brew install kaios
kaios doctor
kaios run "analyze crypto market"
kaios init --template research
kaios config show
kaios run --out artifacts/runtime.md "map the JVM agent runtime"
kaios run --context README.md --out artifacts/project.md "summarize this project"
kaios ps <run-id>
kaios report <run-id>
kaios export <run-id>

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
Release ZIP: https://github.com/morning-verlu/KAI/releases/download/v0.1.10/kaios-0.1.10.zip
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

The current v0.1 demo runs a planner -> executor -> validator workflow and lets you inspect each agent process with PID, token usage, context size, syscall count, duration, lifecycle events, and optional project context sources.

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

The first version is intentionally small but runnable: a default planner -> executor -> validator workflow, process metrics, lifecycle events, permissioned tools, bounded project context, and JSON run snapshots.

I would love feedback on the Kotlin API/DSL and runtime model.

https://github.com/morning-verlu/KAI
https://morning-verlu.github.io/KAI/
```

## First Demo Flow

Install-first:

```bash
brew tap morning-verlu/tap
brew install kaios
kaios doctor
kaios run "analyze crypto market"
kaios run --context README.md --out artifacts/project.md "summarize this project"
kaios ps <run-id>
kaios inspect <run-id>
kaios report <run-id>
```

Hosted installer:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios doctor
kaios run "analyze crypto market"
kaios run --context README.md --out artifacts/project.md "summarize this project"
kaios ps <run-id>
kaios inspect <run-id>
kaios report <run-id>
```

Download ZIP:

```bash
curl -L -o kaios-0.1.10.zip https://github.com/morning-verlu/KAI/releases/download/v0.1.10/kaios-0.1.10.zip
unzip kaios-0.1.10.zip
./kaios-0.1.10/bin/kaios doctor
./kaios-0.1.10/bin/kaios run "analyze crypto market"
./kaios-0.1.10/bin/kaios init --template research
./kaios-0.1.10/bin/kaios config show
./kaios-0.1.10/bin/kaios run --out artifacts/runtime.md "map the JVM agent runtime"
./kaios-0.1.10/bin/kaios run --context README.md --out artifacts/project.md "summarize this project"
```

Build from source:

```bash
git clone https://github.com/morning-verlu/KAI.git
cd KAI
./gradlew test installDist
build/install/kaios-cli/bin/kaios doctor
build/install/kaios-cli/bin/kaios run "analyze crypto market"
build/install/kaios-cli/bin/kaios run --context README.md --out artifacts/project.md "summarize this project"
build/install/kaios-cli/bin/kaios ps <run-id>
build/install/kaios-cli/bin/kaios inspect <run-id>
build/install/kaios-cli/bin/kaios report <run-id>
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
