# Model Providers

KAI OS uses `ModelProvider` as the runtime boundary between agent processes and model execution.

The default provider is deterministic and local:

```bash
build/install/kaios-cli/bin/kaios run "analyze crypto market"
```

No API key is needed unless you opt into a real provider.

## Provider Selection

Set `KAIOS_MODEL_PROVIDER`:

- `mock` - deterministic local provider, default
- `openai` - OpenAI-compatible chat completions provider
- `ollama` - local Ollama chat provider

## OpenAI-Compatible Provider

This provider calls a `/chat/completions` endpoint and is intended for OpenAI-compatible APIs.

```bash
export KAIOS_MODEL_PROVIDER=openai
export OPENAI_API_KEY="..."
export OPENAI_MODEL="your-model"
# optional, defaults to https://api.openai.com/v1
export OPENAI_BASE_URL="https://api.openai.com/v1"

build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

Environment variables:

- `OPENAI_API_KEY` - required
- `OPENAI_MODEL` - required
- `OPENAI_BASE_URL` - optional
- `OPENAI_TEMPERATURE` - optional

The provider does not log API keys.

## Ollama Provider

This provider calls Ollama's local `/api/chat` endpoint.

```bash
export KAIOS_MODEL_PROVIDER=ollama
export OLLAMA_MODEL="your-local-model"
# optional, defaults to http://localhost:11434
export OLLAMA_BASE_URL="http://localhost:11434"

build/install/kaios-cli/bin/kaios run "summarize JVM agent infrastructure"
```

Environment variables:

- `OLLAMA_MODEL` - required
- `OLLAMA_BASE_URL` - optional
- `OLLAMA_TEMPERATURE` - optional

## Current Scope

The v0.1 provider implementations return text output and token usage when available. Tool calling through provider-native function-call APIs is intentionally left for a later runtime design pass, because KAI OS treats tools as explicit syscall boundaries.
