# Examples

These examples use the deterministic mock model provider, so no API key is required.

## Run the Default Workflow

```bash
./gradlew installDist
build/install/kaios-cli/bin/kaios run "analyze crypto market"
```

The default workflow is:

```text
planner -> executor -> validator
```

Each node becomes an agent process. The CLI writes a JSON snapshot under `.kaios/runs/`.

## Inspect Processes

```bash
build/install/kaios-cli/bin/kaios ps <run-id>
```

The process table shows:

- PID
- agent name
- lifecycle state
- token usage
- context size
- syscall count
- duration

## Inspect Events

```bash
build/install/kaios-cli/bin/kaios inspect <run-id>
```

The event log shows process lifecycle transitions and syscall activity.

## Try Other Tasks

```bash
build/install/kaios-cli/bin/kaios run "draft a release plan"
build/install/kaios-cli/bin/kaios run "summarize JVM agent infrastructure"
build/install/kaios-cli/bin/kaios run "design a safe file tool"
```

## Use a Real Provider

OpenAI-compatible endpoint:

```bash
export KAIOS_MODEL_PROVIDER=openai
export OPENAI_API_KEY="..."
export OPENAI_MODEL="your-model"
build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

Ollama:

```bash
export KAIOS_MODEL_PROVIDER=ollama
export OLLAMA_MODEL="your-local-model"
build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

See [../docs/PROVIDERS.md](../docs/PROVIDERS.md) for provider details.
