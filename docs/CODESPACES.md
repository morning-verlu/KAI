# GitHub Codespaces

Use Codespaces when you want to try KAI OS without installing Java, Gradle, or the release ZIP locally.

[Open KAI OS in GitHub Codespaces](https://codespaces.new/morning-verlu/KAI?quickstart=1)

The dev container uses Java 17 and runs:

```bash
./gradlew :kaios-cli:installDist --no-daemon
```

After the workspace finishes creating, run the no-key tour:

```bash
build/install/kaios-cli/bin/kaios tour
```

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
./gradlew :kaios-cli:installDist --no-daemon
```
