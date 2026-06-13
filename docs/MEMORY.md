# Memory Stores

KAI OS uses `MemoryStore` as the state boundary for agent process memory.

## Session Memory

`SessionMemoryStore` is the default. It stores memory in process memory for the lifetime of a CLI run.

Use it when:

- you want deterministic local demos
- you do not need memory after the process exits
- you are writing fast unit tests

```bash
build/install/kaios-cli/bin/kaios run "analyze crypto market"
```

## SQLite Memory

`SQLiteMemoryStore` persists memory entries to a local SQLite database.

Use it when:

- you want memory to survive process restarts
- you want to inspect agent memory from local tooling
- you are building a longer-running local runtime

```bash
export KAIOS_MEMORY_STORE=sqlite
# optional, defaults to .kaios/kaios.db
export KAIOS_SQLITE_PATH=".kaios/kaios.db"

build/install/kaios-cli/bin/kaios run "draft a launch plan"
```

The adapter stores:

- run id
- agent id
- role
- content
- timestamp

It supports the same `MemoryStore` interface:

- `append`
- `read`
- `clear`

## Schema

The SQLite schema is initialized automatically:

```sql
CREATE TABLE IF NOT EXISTS memory_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    run_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp TEXT NOT NULL
);
```

KAI OS also creates an index for run, agent, timestamp, and insertion order.
