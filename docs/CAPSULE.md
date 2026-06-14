# KAI Run Capsule

`kaios capsule` turns a saved run snapshot into a portable evidence package.

It is the audit layer above snapshots and traces:

```text
snapshot + process trace + provenance hashes + replay commands = run capsule
```

Create a capsule from the newest local run:

```bash
kaios capsule latest
```

By default, KAI OS writes:

```text
.kaios/capsules/<run-id>.capsule.json
```

Print JSON to stdout instead:

```bash
kaios capsule latest --json
```

Write to an explicit artifact path:

```bash
kaios capsule latest --out artifacts/run.capsule.json --force
```

Validate the capsule contract without writing a file:

```bash
kaios capsule latest --check
```

Validate a shared capsule file without the original `.kaios/runs/` snapshot:

```bash
kaios capsule --file artifacts/run.capsule.json --check
```

Print a shared capsule file back as normalized JSON:

```bash
kaios capsule --file artifacts/run.capsule.json --json
```

## Schema

Current schema id:

```text
kaios.run-capsule/v1
```

Top-level fields:

| Field | Meaning |
| --- | --- |
| `schema` | Capsule schema id. |
| `version` | KAI OS CLI version that generated the capsule. |
| `generatedAt` | ISO-8601 generation time. |
| `cwd` | Working directory used by the CLI. |
| `run` | Compact run summary with workflow, success, process count, tokens, context bytes, syscalls, and duration. |
| `provenance` | Snapshot path, saved snapshot SHA-256, embedded snapshot SHA-256, trace SHA-256, and optional project config hash/validation metadata. |
| `replay` | Commands for inspecting the same run without re-running agents. |
| `validation` | Capsule and trace contract status. |
| `snapshot` | Full saved `.kaios/runs/<run-id>.json` payload. |
| `trace` | Full `kaios.process-trace/v1` payload generated from the snapshot. |

## Why It Matters

Run capsules make KAI OS harder to reduce to a chatbot or wrapper:

- Agent work becomes an inspectable runtime artifact.
- CI can keep stable evidence for each agent workflow.
- Teams can attach a single JSON file to issues, pull requests, release notes, or future Agent Desktop imports.
- Replays do not need API keys because capsules are generated from saved snapshots.
- Shared capsules can be validated with `--file` even when the original run directory is not present.

## CI Pattern

After a readiness gate:

```bash
kaios verify
kaios capsule latest --check
kaios capsule latest --out artifacts/kaios-run.capsule.json --force
kaios capsule --file artifacts/kaios-run.capsule.json --check
```

The first command proves the runtime can execute a deterministic workflow. The capsule commands prove the saved run can produce and re-validate a stable audit package.
