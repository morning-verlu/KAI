# Outbound Launch Kit

Use this page when announcing KAI OS. The launch goal is real feedback and real stars from Kotlin/JVM developers, OSS maintainers, and agent infrastructure builders. For the shortest next-action queue, use [launch-posts/post-now.md](launch-posts/post-now.md).

## Canonical Pitch

KAI OS is a local-first Evidence OS for AI agents in Kotlin. It turns agent runs into process traces, replayable capsules, syscall ledgers, and CI-ready runtime evidence.

```text
Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state
```

## Main CTA

For cold audiences, lead with the no-install Evidence Viewer:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

The viewer shows the product surface before asking anyone to install Java, Gradle, Docker, Codespaces, or an API key: process table, syscall ledger, replayable capsule, and baseline gate.

For hands-on audiences, lead to the no-key tour:

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
```

The tour creates a disposable Git repo, runs the Evidence OS loop, generates a review artifact, writes a process trace, writes a replayable capsule, prints a process table, and shows recovery/evidence commands.

No local Java setup fallback:

```text
https://codespaces.new/morning-verlu/KAI?quickstart=1
```

Docker fallback:

```bash
docker build -t kaios:local .
docker run --rm kaios:local tour
```

## Links

- Repo: https://github.com/morning-verlu/KAI
- Site: https://morning-verlu.github.io/KAI/
- Evidence viewer: https://morning-verlu.github.io/KAI/evidence-viewer.html
- Start here: https://github.com/morning-verlu/KAI/blob/main/START_HERE.md
- Evaluate in 5 minutes: https://github.com/morning-verlu/KAI/blob/main/docs/EVALUATE.md
- Codespaces: https://codespaces.new/morning-verlu/KAI?quickstart=1
- Release: https://github.com/morning-verlu/KAI/releases/tag/v0.3.1
- Installer: https://morning-verlu.github.io/KAI/install.sh
- Demo GIF: https://morning-verlu.github.io/KAI/assets/kaios-demo.gif
- Social card: https://morning-verlu.github.io/KAI/assets/kaios-social-card.png
- Product-proof image: https://morning-verlu.github.io/KAI/assets/kaios-evidence-proof.png
- Evidence map PNG: https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
- Evidence map SVG: https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.svg
- Evidence OS: https://github.com/morning-verlu/KAI/blob/main/docs/EVIDENCE_OS.md
- Trust matrix: https://github.com/morning-verlu/KAI/blob/main/docs/TRUST_MATRIX.md
- Kotlin Runtime API: https://github.com/morning-verlu/KAI/blob/main/docs/KOTLIN_API.md
- Comparison: https://github.com/morning-verlu/KAI/blob/main/docs/COMPARISON.md
- Launch metrics: https://github.com/morning-verlu/KAI/blob/main/docs/LAUNCH_METRICS.md
- Kotlin runtime API example: https://github.com/morning-verlu/KAI/tree/main/examples/kotlin-runtime-api
- JVM service review example: https://github.com/morning-verlu/KAI/tree/main/examples/jvm-service-review
- Baseline gate example: https://github.com/morning-verlu/KAI/tree/main/examples/baseline-gate
- Local evidence samples smoke: `./scripts/evidence-samples-smoke.sh`
- Codespaces/browser smoke: `./scripts/codespaces-smoke.sh`
- Docker smoke: `./scripts/docker-smoke.sh`
- Trust contract: https://github.com/morning-verlu/KAI/blob/main/docs/TRUST.md

## Do Not Lead With

- Homebrew. It is an install option, not the product.
- "Kotlin LangChain." KAI OS is lower-level runtime evidence infrastructure.
- A full feature dump. Lead with the tour and the evidence artifacts.
- A claim that it is production complete. Be honest: v0.3.1 is small, runnable, and focused.

## Metrics Loop

Before posting, then again at +2h, +24h, and +72h:

```bash
./scripts/launch-metrics.sh
```

For X posts, pass visible analytics from the browser:

```bash
X_POST_URL="https://x.com/wurslu/status/..." \
X_IMPRESSIONS=1 \
X_ENGAGEMENTS=0 \
X_LINK_CLICKS=0 \
./scripts/launch-metrics.sh
```

Read [Launch Metrics](LAUNCH_METRICS.md) for the decision rules.

## Day 0 Posting Order

1. Update the GitHub launch issue with this outbound plan.
2. Post the GitHub Discussion as the canonical project announcement.
3. Post the X/LinkedIn short version with the demo GIF.
4. Post the Kotlin/JVM community version.
5. Post Show HN after the README and site are stable.
6. Reply quickly to every real question for the first 24 hours.

## Copy-Ready Drafts

Each channel draft is also available as a standalone file so posting does not require editing this long launch kit:

- [First external wave](launch-posts/first-wave.md)
- [Evidence Viewer-first posts](launch-posts/evidence-viewer-first.md)
- [Short social post](launch-posts/short-social.md)
- [Second wave visual post](launch-posts/second-wave.md)
- [Community wave](launch-posts/community-wave.md)
- [X / LinkedIn thread](launch-posts/x-linkedin-thread.md)
- [Kotlin community post](launch-posts/kotlin-community.md)
- [Show HN post](launch-posts/show-hn.md)
- [Reddit post](launch-posts/reddit.md)

## Short Social Post

```text
I built KAI OS: a local-first Evidence OS for AI agents in Kotlin.

It treats agent work like OS runtime evidence:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

Run a no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable repo and outputs a process table, Markdown review, process trace, replayable capsule, and CI-style evidence summary.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```

Attach this image when the platform supports image posts:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```

## Visual-First Short Post

```text
KAI OS turns agent runs into runtime evidence:

Git change -> kaios review -> review.md + trace.json + capsule.json -> review/audit/baseline gate

No API key needed for the first-run path.

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

Repo: https://github.com/morning-verlu/KAI
Start here: https://github.com/morning-verlu/KAI/blob/main/START_HERE.md
```

Attach:

```text
https://morning-verlu.github.io/KAI/assets/kaios-evidence-map.png
```

## X / LinkedIn Thread

```text
1/ I built KAI OS: a local-first Evidence OS for AI agents in Kotlin.

The idea: agent work should be inspectable like operating-system processes, not just text from a chatbot.

2/ The runtime model:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

3/ The first-run path needs no API key:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

4/ `kaios tour` creates a disposable Git repo, runs a current-change review, and writes:

- Markdown review artifact
- process trace
- replayable capsule
- process table
- evidence summary
- recovery dry-run report

5/ The moat is evidence, not more agents:

- process recovery evidence
- priority scheduler evidence
- syscall audit ledger
- offline replay
- baseline diff gates
- PR-friendly evidence summaries

6/ I would love feedback from Kotlin/JVM developers, OSS maintainers, and people building agent infrastructure.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```

## Show HN Draft

```text
Show HN: KAI OS - Local-first Evidence OS for AI agents in Kotlin

I am building KAI OS, a Kotlin/JVM runtime that turns AI agent runs into process traces, replayable capsules, syscall ledgers, and CI-ready runtime evidence.

The core model is:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current v0.3.1 release is small but runnable. The easiest way to try it is the no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable Git repo, runs a deterministic current-change review, and writes a Markdown review artifact, process trace, replayable capsule, process table, evidence summary, and recovery dry-run report.

The point is not to be another Kotlin LangChain clone. I am trying to build the local evidence layer around agent work: what ran, which tools were called, what failed, what recovered, what can be replayed offline, and what can be gated in CI.

I would love feedback from Kotlin/JVM developers, OSS maintainers, and people thinking about agent runtime infrastructure.

Repo: https://github.com/morning-verlu/KAI
Site: https://morning-verlu.github.io/KAI/
```

## Kotlin / JVM Community Draft

```text
I am building KAI OS, a Kotlin/JVM runtime for AI agent evidence.

It is not trying to be a Kotlin LangChain clone. The runtime model is closer to OS infrastructure:

Agent = Process
Workflow = Scheduler
Tool = Syscall
Memory = Process state

The v0.3.1 release has a no-key first-run tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

It generates a process table, trace JSON, replayable capsule, current-change review artifact, and CI-style evidence summary from a disposable local repo.

I would especially like feedback on the Kotlin API shape, the process/scheduler/tool model, and whether local evidence capsules are useful for JVM backend teams.

https://github.com/morning-verlu/KAI
https://morning-verlu.github.io/KAI/
```

## Reddit Draft

```text
Title: KAI OS - local-first Evidence OS for AI agents in Kotlin

I am building KAI OS, a Kotlin/JVM runtime that treats AI agent runs like inspectable processes.

The model:

- Agent = Process
- Workflow = Scheduler
- Tool = Syscall
- Memory = Process state

The current release is intentionally small and local-first. It has a deterministic no-key tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour creates a disposable Git repo and outputs a current-change review artifact, process table, trace JSON, replayable capsule, evidence summary, and recovery dry-run report.

The main difference from agent frameworks is the focus on portable evidence: replay, baseline diffs, syscall audit records, process recovery evidence, and CI-ready summaries.

I would love feedback, especially from Kotlin/JVM developers and maintainers who care about agent runs being auditable and reproducible.

Repo: https://github.com/morning-verlu/KAI
```

## GitHub Discussion Update

```text
KAI OS v0.3.1 now has a first-run tour:

curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour

The tour runs without an API key, creates a disposable Git repo, and produces the core Evidence OS artifacts: process table, Markdown review, trace JSON, replayable capsule, evidence summary, and recovery dry-run report.

I am looking for feedback on three things:

1. Does the Agent = Process / Workflow = Scheduler / Tool = Syscall model feel useful?
2. Would replayable run capsules help in PR review, support, or CI?
3. What would make `kaios review` valuable enough to run on a real Kotlin/JVM repo?
```

## Reply Playbook

Use short, concrete replies.

| Question | Reply direction |
| --- | --- |
| Is this another LangChain? | No. KAI OS focuses on local runtime evidence: process traces, capsules, syscall audit, replay, and CI gates. |
| Does it need an API key? | The default tour/review/evidence path does not. Real providers are optional. |
| Why Kotlin? | JVM teams need agent runtime infrastructure that fits their build, CI, and backend ecosystem. |
| Is it production ready? | Not yet. v0.3.1 is a runnable evidence-core seed. The goal is feedback on the runtime model and evidence artifacts. |
| Why not build UI first? | The evidence contract comes first; UI can later read the same traces, capsules, syscalls, and recovery records. |

## Metrics To Capture

Capture before posting, then at 1 hour, 6 hours, 24 hours, and 7 days:

- GitHub stars
- forks
- release downloads
- site visits if available
- discussion comments
- issues opened
- install failures
- questions repeated by more than one person

## Follow-Up Releases

Plan one visible follow-up within 24-48 hours of the first external post. Good candidates:

- improve one confusing first-run message.
- add one example capsule to the repo.
- add one Kotlin/JVM sample workflow.
- convert one repeated question into docs.
- ship one small `kaios review` improvement found by early users.
