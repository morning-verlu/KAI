# Why Star KAI OS

KAI OS is worth a star if you believe agent work should leave runtime evidence that survives beyond a chat transcript.

The project is not trying to be another chatbot framework or Kotlin LangChain clone. It is building a local-first Evidence OS for AI agents in Kotlin:

```text
Agent    = Process
Workflow = Scheduler
Tool     = Syscall
Memory   = Process state
```

## The Bet

As agents move into code review, CI, operations, and internal automation, teams will need more than "the model said so."

They will need:

- process traces that show what ran.
- replayable capsules that travel with a bug report or PR.
- syscall ledgers that show which tools were allowed, denied, and timed.
- baseline gates that catch stable runtime behavior drift in CI.
- local-first evidence that works before any hosted provider or API key is configured.

KAI OS is an early Kotlin/JVM attempt at that evidence layer.

## Star If You Want This To Exist

Star KAI OS if one of these feels useful:

- You build Kotlin/JVM services and want agent infrastructure with typed runtime boundaries.
- You maintain an open-source repo and want AI review artifacts that can be inspected, replayed, and compared.
- You care about agent observability, but want portable local artifacts instead of only hosted dashboards.
- You want tool access to look more like capabilities and syscalls than arbitrary model actions.
- You think CI should be able to gate agent runtime behavior, not just compile code.

## Watch If You Want The Next Milestones

The near-term roadmap is focused on making the evidence loop sharper, not on adding a chat UI:

- richer `kaios review` artifacts for real pull requests.
- stronger capsule replay and baseline diff contracts.
- more syscall sandbox examples.
- public CI templates once GitHub workflow publishing is unblocked.
- early contributor issues for docs, examples, and Kotlin runtime recipes.

## Fork If You Want To Build On It

Good first areas:

- add a new evidence walkthrough.
- write a Kotlin API recipe.
- improve the Codespaces first-run path.
- add a safe tool integration behind capability grants.
- try the runtime model against your own JVM service and report what feels missing.

Start with:

- [Start here](../START_HERE.md)
- [Evaluate in 5 minutes](EVALUATE.md)
- [Trust Matrix](TRUST_MATRIX.md)
- [Kotlin Runtime API](KOTLIN_API.md)
- [Good first issues](https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22good%20first%20issue%22)

## Skip It If

KAI OS is intentionally early and low-level. It is probably not the right project if you need:

- a polished hosted chatbot product.
- a mature provider marketplace.
- a visual workflow builder today.
- a production agent platform with managed infrastructure.

That is the point of the star test: star it if the Evidence OS direction should exist, even while the implementation is still small.
