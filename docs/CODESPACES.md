# GitHub Codespaces

Use Codespaces when you want to try KAI OS without installing Java, Gradle, or the release ZIP locally.

[Open KAI OS in GitHub Codespaces](https://codespaces.new/morning-verlu/KAI?quickstart=1)

The dev container uses Java 17 and runs:

```bash
./gradlew installDist --no-daemon
```

First-time Codespaces setup can take a few minutes while the dev container installs Java dependencies and builds the CLI. Wait for the terminal prompt to return before running the tour commands.

After the workspace finishes creating, run the no-key tour:

```bash
build/install/kaios-cli/bin/kaios tour
```

To verify the full browser-only path with one command:

```bash
./scripts/codespaces-smoke.sh
```

The smoke script builds the CLI, runs `kaios tour --json`, validates the generated tour capsule, replays it offline, validates the checked-in Evidence Sample, and replays that sample offline.

Useful follow-up commands:

```bash
build/install/kaios-cli/bin/kaios next
build/install/kaios-cli/bin/kaios quickstart --no-ci
build/install/kaios-cli/bin/kaios capsule --file examples/evidence-sample/change-review.capsule.json --check
build/install/kaios-cli/bin/kaios replay --file examples/evidence-sample/change-review.capsule.json
```

The Codespaces path still uses the deterministic mock provider by default, so no model API key is required.

If the dev container build is interrupted, rerun:

```bash
./gradlew installDist --no-daemon
```

If you run a `kaios` command before the build finishes and see `No such file or directory`, wait for the container setup to complete, then run:

```bash
./scripts/codespaces-smoke.sh
```

The smoke script rebuilds the CLI when needed and verifies the browser-only path end to end.

For the complete evaluator path, read [START_HERE.md](../START_HERE.md).
