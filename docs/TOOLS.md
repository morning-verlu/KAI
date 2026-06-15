# Syscall Tools

KAI OS treats tools as syscalls: an agent process can only call a registered tool when the workflow grants that tool and the matching runtime permission.

v0.3 adds a syscall ledger. Every allowed or denied call records call id, PID, agent, tool, permission, redacted arguments, duration, estimated cost, and error. These records are embedded in process traces, run capsules, `kaios review --json`, and evidence summaries.

## Built-In Syscalls

- `echo`: returns a supplied message. Useful for deterministic local demos and validation.
- `clock`: returns the current UTC timestamp.
- `mock-http`: returns a deterministic mocked HTTP response. No network access.
- `http`: performs real allowlisted HTTP `GET`, `HEAD`, and `POST` requests.
- `file`: reads, writes, lists, and checks files inside `.kaios/files`.

## Allowlisted HTTP

Real HTTP is disabled by default. Enable it by setting `KAIOS_HTTP_ALLOWLIST` to comma, semicolon, or newline separated rules:

```bash
export KAIOS_HTTP_ALLOWLIST="example.com,https://api.github.com/repos/morning-verlu/KAI"
```

Rules can be exact hosts, wildcard hosts, or URL prefixes:

```text
example.com
*.example.com
https://api.example.com/v1
```

The HTTP syscall rejects:

- hosts not present in `KAIOS_HTTP_ALLOWLIST`.
- non-absolute URLs.
- non-HTTP schemes.
- methods other than `GET`, `HEAD`, and `POST`.

Responses are bounded to 20,000 characters by default so tool output cannot flood process memory.

## Workflow Config Example

```json
{
  "name": "http-research",
  "agents": [
    {
      "id": "researcher",
      "instruction": "Fetch allowlisted evidence and summarize it.",
      "tools": ["http", "echo"],
      "dependsOn": []
    },
    {
      "id": "validator",
      "instruction": "Check the response and note missing evidence.",
      "tools": ["echo"],
      "dependsOn": ["researcher"]
    }
  ]
}
```

For tighter grants, use `capabilities` instead of plain `tools`:

```json
{
  "id": "reviewer",
  "capabilities": [
    {
      "tool": "echo",
      "permission": "ECHO",
      "scope": "*",
      "maxCalls": 3,
      "estimatedCostMicros": 0
    }
  ]
}
```

Capability scope and limits can only narrow access. They do not bypass the HTTP allowlist, file root, `.kaiosignore`, or built-in syscall safety checks.

Run it with:

```bash
export KAIOS_HTTP_ALLOWLIST="example.com"
kaios run --config http-research.json "summarize https://example.com"
kaios inspect
```

`kaios doctor` prints whether real HTTP syscalls are disabled or which allowlist rules are active.

## Real Provider Syscall Directives

OpenAI-compatible and Ollama providers can request syscalls with a plain-text directive:

```text
KAIOS_SYSCALL http method=GET url=https://example.com
KAIOS_SYSCALL echo message="validated evidence"
```

KAI OS strips directive lines from the agent's normal text output, executes the requested tools through the same permission checks, records syscall events, and appends syscall results to the process output.

This is intentionally simpler than provider-native function calling in v0.1. It keeps the runtime boundary visible while still letting real models use tools.

## Scoped File Access

The `file` syscall is rooted under `.kaios/files` by default. Absolute paths and path traversal are rejected:

```json
{
  "id": "writer",
  "tools": ["file"]
}
```

Supported operations:

- `read`
- `write`
- `list`
- `exists`

The scoped root keeps agent file IO separate from project context. Use `kaios run --context` when an agent should read project files as input; use the `file` syscall when an agent should write runtime artifacts.
