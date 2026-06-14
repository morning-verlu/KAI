# Launch Plan

The project goal is to make KAI OS legible as a new infrastructure category: an AI Agent Operating System in Kotlin.

## Positioning

Lead with:

```text
AI Agent Operating System in Kotlin
Agent = Process, Workflow = Scheduler, Tool = Syscall
```

Avoid positioning it as:

- a chatbot framework
- a LangChain Kotlin clone
- a thin SDK wrapper

## Launch Channels

- GitHub Pages launch site: https://morning-verlu.github.io/KAI/
- GitHub README and topics
- Hacker News "Show HN"
- Reddit: r/Kotlin, r/MachineLearning, r/LocalLLaMA, r/programming
- Kotlin Slack
- LinkedIn/Twitter/X demo thread
- DEV.to or Medium technical post

## Demo Hook

Use the process table:

```bash
kaios run "analyze crypto market"
kaios ps latest
kaios trace latest
kaios capsule latest --out artifacts/run.capsule.json --force
kaios capsule --file artifacts/run.capsule.json --check
kaios replay --file artifacts/run.capsule.json
```

The process table is the visual proof of the idea. The trace is the durable proof: `kaios.process-trace/v1` turns one agent run into a reusable audit, CI, replay, and future UI asset. The capsule is the moat proof: `kaios.run-capsule/v1` packages the snapshot, trace, provenance hashes, and replay commands as a portable runtime artifact that can be validated from a shared JSON file. Replay is the protocol proof: `kaios.run-replay/v1` rebuilds the trace from the embedded snapshot without API keys or the original run directory.

## First-Star Checklist

- CI green
- install command works
- README has a clear first screen
- GitHub Pages has a direct launch landing page
- social preview image works for shared links
- demo GIF shows run, ps, and inspect
- release ZIP/TAR assets are attached for download-first trial
- install script supports one-command local setup
- Homebrew tap supports `brew install kaios`
- `kaios doctor` helps new users diagnose local setup
- architecture diagram is visible
- example output is included
- roadmap is public
- issues are open for provider/tool/plugin requests

## 1000-Star Reality

Stars should come from real interest, not automation or artificial engagement. The controllable path is:

- sharp concept
- working demo
- strong README
- repeated launch posts
- short video or GIF of process table
- quick follow-up releases
- visible issue responses

See [LAUNCH_KIT.md](LAUNCH_KIT.md) for copy-paste launch posts and the first demo flow.
