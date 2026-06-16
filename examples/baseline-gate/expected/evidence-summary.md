## KAI OS Evidence

### Verdict

Different. Evidence is valid, but stable runtime behavior changed against the baseline.

- Run: `<current-run-id>`
- Capsule: `examples/baseline-gate/capsules/current-different.capsule.json`
- Replay: `valid`
- Baseline diff: `different`
- Tool time: `0ms`
- Estimated cost: `0`
- Denied syscalls: `0`
- Recovered processes: `0`

### Changed Runtime Behavior

- `task`: `review billing risk service runtime behavior Workspace Index: - 8 files, 234 ...` -> `review billing risk service runtime behavior Workspace Index: - 8 files, 250 ...`
- `finalOutputSha256`: `82f978cdaea0c93797c95d9242ab634f6f10203d658b7cd11bc5c2e6328f4950` -> `dbf75dd22e9f7bd3d558f523cb242071a20135256ab6d8c611c67d337c2bf942`
- `metrics.tokenTotal`: `2054` -> `2270`
- `metrics.inputTokens`: `1970` -> `2186`
- `metrics.contextBytes`: `23055` -> `26115`

### Fix First

`kaios diff examples/baseline-gate/capsules/baseline.capsule.json examples/baseline-gate/capsules/current-different.capsule.json --check`

### Process Table

| PID | Agent | State | Tokens | Memory | Syscalls | Tool ms | Cost | Duration |
| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | `inspector` | `SUCCEEDED` | 732 | 8432 | 1 | 0 | 0 | 5ms |
| 2 | `reviewer` | `SUCCEEDED` | 746 | 8564 | 1 | 0 | 0 | 0ms |
| 3 | `validator` | `SUCCEEDED` | 792 | 9119 | 1 | 0 | 0 | 1ms |
