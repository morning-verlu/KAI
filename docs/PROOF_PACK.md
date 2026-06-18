# KAI OS Proof Pack

Use this page when you need the shortest proof that KAI OS is a product surface, not just a slogan.

KAI OS is early, but the core claim is already checkable:

```text
KAI OS  = Evidence OS for AI agents
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Run      = Evidence
```

The proof pack is intentionally local-first. You can inspect checked-in artifacts, run deterministic smoke checks, and replay capsules without an API key or a hosted agent service.

## The Five Proofs

| Claim | Proof today | Where to check |
| --- | --- | --- |
| No API key is required for the first run | deterministic mock provider and `kaios tour` | `kaios tour` |
| Agents are inspectable as processes | process rows with PID, state, tokens, context, syscalls, worker id, and lifecycle events | `examples/evidence-sample/change-review.trace.json` |
| Tools are syscall-bounded | syscall ledger records tool, permission, allowed/denied status, redacted args, duration, and cost | `examples/evidence-sample/change-review.trace.json` |
| Runs are portable | run capsule embeds snapshot, trace, provenance hashes, and replay commands | `examples/evidence-sample/change-review.capsule.json` |
| CI can gate runtime drift | baseline/current capsules produce a stable diff and `--check` exits nonzero on changed behavior | `examples/baseline-gate/expected/diff.stable.json` |

## Fastest No-Install Path

Open the visual proof first:

```text
https://morning-verlu.github.io/KAI/evidence-viewer.html
```

It shows one checked-in run as:

- process table
- syscall ledger
- replayable capsule
- offline replay status
- baseline gate result

## Fastest Local Path

```bash
curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
export PATH="$HOME/.kaios/bin:$PATH"
kaios tour
```

`kaios tour` creates a disposable Git workspace, runs the no-key Evidence OS loop, and prints artifact paths for review, trace, capsule, evidence summary, and recovery dry-run output.

## Fastest Source-Checkout Verification

```bash
./scripts/evidence-samples-smoke.sh
```

This validates:

- checked-in Evidence Sample capsule contract
- offline replay for the Evidence Sample
- baseline/current capsule contracts
- offline replay for baseline/current capsules
- normalized stable `kaios.run-diff/v1` output
- `kaios diff --check` exiting `1` for valid changed runtime behavior

For the full repository trust path:

```bash
./scripts/repository-ci-smoke.sh
```

That also builds and tests the project, installs the CLI, runs the Kotlin runtime API example, runs `kaios tour`, validates the generated tour capsule, and replays it offline.

## Checked-In Artifacts

| Artifact | Why it matters |
| --- | --- |
| `examples/evidence-sample/change-review.md` | human-readable review artifact from `kaios review` |
| `examples/evidence-sample/change-review.trace.json` | process trace with processes, scheduler, syscalls, cost, and events |
| `examples/evidence-sample/change-review.capsule.json` | portable replay capsule |
| `examples/evidence-sample/review-result.json` | stable `kaios.review/v1` CLI/CI contract |
| `examples/baseline-gate/capsules/baseline.capsule.json` | known-good runtime behavior |
| `examples/baseline-gate/capsules/current-different.capsule.json` | changed runtime behavior |
| `examples/baseline-gate/expected/diff.stable.json` | normalized baseline-gate proof |
| `examples/kotlin-runtime-api` | embeddable Kotlin/JVM runtime API example |

## What This Proves

KAI OS does not prove that an agent answer is correct. It proves something narrower and more useful for infrastructure:

- what processes ran
- which tools were requested
- which syscalls were allowed or denied
- what runtime metrics changed
- whether a run capsule can replay offline
- whether stable runtime behavior drifted from a baseline

That is the product boundary: portable agent-run evidence for developers, reviewers, maintainers, and CI.

## Evidence Glossary

These are the main artifacts KAI OS produces. If you have ever looked at a CI log and wished it told you *what actually happened* instead of just pass/fail, this is the vocabulary you need.

**Review artifact** — The Markdown file you get after `kaios review`. It is a human-readable summary of what the agent did: which files it looked at, what processes ran, and what it concluded. Think of it as the agent's pull request comment, written for people who do not want to open JSON.

**Process trace** — A structured JSON record (`kaios.process-trace/v1`) of every process that ran during a workflow. It tracks PIDs, state transitions, token counts, context size, syscall totals, cost, and lifecycle events. If review artifacts are the summary, the process trace is the detailed receipt.

**Syscall ledger** — The audit log inside a process trace that records every tool call an agent made. Each entry says which tool was requested, whether the runtime allowed or denied it, how long it took, and what it cost. It is called a "syscall" ledger because KAI OS treats tool calls the way an operating system treats system calls — as a permission boundary.

**Replay capsule** — A portable JSON package (`kaios.run-capsule/v1`) that bundles the run snapshot, the process trace, provenance hashes, and replay commands into one file. You can copy it to a different machine and replay it offline without an API key. It is proof that a run happened, and proof that the evidence has not been tampered with.

**Baseline diff** — The output of `kaios diff` when you compare two replay capsules. It ignores noise like timestamps and run ids, and focuses on whether the stable runtime behavior actually changed — things like process paths, token counts, syscall counts, and final output hashes. CI uses `--check` to fail the build when the diff is not empty.

**Evidence summary** — The compact Markdown report from `kaios evidence --summary`. It is designed to be pasted into a PR description or a CI step summary. It contains a verdict, a list of changed runtime behaviors (if any), a "fix first" section, and a process table. One glance tells you if the run is clean.

**Recovery dry-run** — The output of `kaios recover --dry-run`. When an agent process crashes during a run, the runtime records the failure. The dry-run does not restart anything — it just explains which processes crashed, what recovery evidence exists, and what commands you would run to actually recover. It is read-only on purpose, so you can inspect before you act.

For the full JSON schemas behind these artifacts, see [JSON Contracts](JSON_CONTRACTS.md).

## Honest Gaps

- Public GitHub Actions CI is not committed yet because the current token lacks the `workflow` scope. A copyable workflow template exists at `examples/github-actions-repository-ci.yml`.
- Docker smoke is documented, but full image pulls have been slow in the current local network. The lightweight preflight path is `./scripts/docker-smoke.sh --preflight`.
- Real model providers are optional. The first-run proof path stays deterministic and local by default.

## Next Step

If this evidence model is useful, star or watch the repository and run the no-key tour. Early stars help test whether local-first agent evidence is a real Kotlin/JVM infrastructure category, not just a private experiment:

```text
https://github.com/morning-verlu/KAI
```

If you want to challenge the proof, start with the checked-in artifacts above or the focused Kotlin/JVM feedback discussion:

```text
https://github.com/morning-verlu/KAI/discussions/17
```
