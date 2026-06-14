# KAI Run Capsule

`kaios capsule` turns a saved run snapshot into a portable evidence package.

For the product-level audit gate, start with `kaios evidence`. It packages the capsule, validates the capsule contract, replays it offline, and optionally compares it with a baseline in one command:

```bash
kaios evidence latest --out artifacts/run.capsule.json --force
kaios evidence latest --out artifacts/run.capsule.json --baseline artifacts/baseline.capsule.json --check --force
```

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

Replay a shared capsule offline:

```bash
kaios replay --file artifacts/run.capsule.json
kaios replay --file artifacts/run.capsule.json --json
```

Replay validates the capsule contract, rebuilds `kaios.process-trace/v1` from the embedded snapshot, and checks that the rebuilt trace matches the embedded trace. It does not call a model provider and does not need the original `.kaios/runs/` directory.

Compare two shared capsules offline:

```bash
kaios diff artifacts/baseline.capsule.json artifacts/current.capsule.json
kaios diff artifacts/baseline.capsule.json artifacts/current.capsule.json --check
kaios diff --left artifacts/baseline.capsule.json --right artifacts/current.capsule.json --json
```

Diff validates and replays both capsules first, then compares a stable runtime signature. It ignores run ids, timestamps, and duration noise, while comparing workflow, task, success, final output hash, process path, process states, tokens, context, syscalls, and event counts. `--check` exits `1` when valid capsules differ, which makes it useful as a regression gate.

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

Replay output uses schema:

```text
kaios.run-replay/v1
```

It includes the capsule source path, run summary, deterministic replay status, contract issues, replay checks, provenance hashes, rebuilt trace hash, metrics, and path.

Diff output uses schema:

```text
kaios.run-diff/v1
```

It includes both capsule sources, stable evidence hashes, replay checks, metric deltas, a `same` boolean, and field-level differences.

Evidence output uses schema:

```text
kaios.evidence/v1
```

It includes the generated capsule path, capsule validation status, offline replay status, optional baseline diff status, stable evidence hashes, issue lists, and next commands. It is intentionally compact so CI logs can show the result without embedding the full capsule again.

## Why It Matters

Run capsules make KAI OS harder to reduce to a chatbot or wrapper:

- Agent work becomes an inspectable runtime artifact.
- CI can keep stable evidence for each agent workflow.
- Teams can attach a single JSON file to issues, pull requests, release notes, or future Agent Desktop imports.
- Replays do not need API keys because capsules are generated from saved snapshots.
- Shared capsules can be validated with `--file` even when the original run directory is not present.
- `kaios replay` proves the embedded snapshot can deterministically rebuild the embedded trace.
- `kaios diff` proves whether two valid runs changed in stable agent behavior, not timestamp noise.
- `kaios evidence` makes the full proof path one command, which is easier to standardize across CI, reviews, and release gates.

## CI Pattern

After a readiness gate:

```bash
kaios verify --evidence --force
kaios verify --evidence --baseline artifacts/baseline.capsule.json --check --force
```

The first command proves the runtime can execute a deterministic workflow and package the same run as evidence. The baseline command proves the saved run can produce and re-validate a stable audit package, rebuild trace evidence offline, and compare current behavior against a baseline when your CI has one.
