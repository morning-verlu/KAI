# Contributing

Thanks for helping build KAI OS.

This project is early, so the most valuable contributions are focused implementation PRs, tests, examples, and docs that make the Evidence OS loop sharper: agent work in, process trace, replayable capsule, syscall ledger, and CI-grade proof out.

## Local Setup

```bash
./gradlew clean test installDist
build/install/kaios-cli/bin/kaios tour
build/install/kaios-cli/bin/kaios quickstart --no-ci
```

You need Java 17+. The Gradle wrapper is included.

## First Contribution Path

1. Run the no-key tour:

```bash
build/install/kaios-cli/bin/kaios tour
```

2. Pick a scoped issue:

- Good first issues: https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22good%20first%20issue%22
- Help wanted issues: https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22help%20wanted%22
- Feedback issues: https://github.com/morning-verlu/KAI/issues?q=is%3Aissue%20state%3Aopen%20label%3Afeedback

3. Keep the PR small and include the command you used to verify it.

## Good First Areas

- `kaios tour` examples that help new users understand KAI OS in under one minute.
- `kaios review` and `kaios evidence` docs, smoke tests, and edge cases.
- Scheduler, recovery, and syscall-ledger contract tests.
- `.kaiosignore`, bounded context, and safe artifact behavior.
- CLI output polish that makes process evidence easier to scan.
- JVM/Kotlin examples that show KAI OS as an evidence layer beside real agent code.

## Pull Request Expectations

- Keep changes focused.
- Add tests for runtime behavior.
- Run `./gradlew test installDist`.
- Update docs when public behavior changes.

## Design Principles

- Agents are processes, not chat sessions.
- Tools are syscalls, not arbitrary callbacks.
- Workflows are schedulers, not chains.
- Evidence is a product surface, not an observability afterthought.
