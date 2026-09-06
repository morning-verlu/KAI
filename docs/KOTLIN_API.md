# Kotlin Runtime API

Use this path when you want KAI OS as a Kotlin/JVM runtime library instead of starting with the CLI.

The current public example uses a Gradle composite build so it runs directly from this repository without publishing artifacts to Maven first.

```bash
./gradlew -p examples/kotlin-runtime-api run
```

## What The Example Shows

The runnable example lives at [examples/kotlin-runtime-api](../examples/kotlin-runtime-api/).

It demonstrates:

- `AgentSpec` as a process definition.
- `WorkflowScheduler` as a priority-aware DAG scheduler.
- `ToolRegistry` as the syscall boundary.
- `ToolCapabilityGrant` for permissioned tool access.
- `SessionMemoryStore` with process and agent memory scopes.
- `LocalWorkerExecutorBackend(parallelism = 2)` as a local worker simulation.
- `MockModelProvider` for deterministic, no-key execution.

## Minimal Shape

```kotlin
val memory = SessionMemoryStore()
val runtime = AgentRuntime()
val tools = ToolRegistry(listOf(EchoTool(), MockHttpTool()))

val researcher = agent("researcher") {
    memory(memory)
    capability(
        tool = "mock-http",
        permission = ToolPermission.NETWORK,
        scope = "mock://kaios/research",
        limits = ToolCapabilityLimits(maxCalls = 1),
    )
}

val reviewer = agent("reviewer") {
    memory(memory)
    capability(
        tool = "echo",
        permission = ToolPermission.ECHO,
        limits = ToolCapabilityLimits(maxCalls = 1),
    )
}

val flow = workflow("review") {
    node("researcher", researcher).priority(20)
    node("reviewer", reviewer).dependsOn("researcher")
}

val result = WorkflowScheduler(
    runtime = runtime,
    modelProvider = MockModelProvider(),
    tools = tools,
    memory = memory,
    executorBackend = LocalWorkerExecutorBackend(parallelism = 2),
).run(flow, "review a payment retry change")

println(result.processes)
println(result.syscalls)
```
## Custom Tool Capability Recipe

To give an agent access to your own tool, define a `ToolCapabilityGrant`
inside its `capability { }` block. This is the syscall boundary described
above — an agent can only call tools it has an explicit grant for, and only
up to the call limit that grant allows.

```kotlin
val analyst = agent("analyst") {
    memory(memory)
    capability(
        tool = "my-custom-tool",
        permission = ToolPermission.NETWORK,
        scope = "mock://kaios/analysis",
        limits = ToolCapabilityLimits(maxCalls = 1),
    )
}
```

- `tool` names the exact tool this agent may call — access to any other tool is denied by default.
- `permission` categorizes the kind of access being granted (e.g. `NETWORK`, `FILE`, `ECHO`), not just whether access exists.
- `limits.maxCalls` caps how many times the agent may invoke the tool, so a runaway agent can't call it indefinitely.

See [examples/kotlin-runtime-api](../examples/kotlin-runtime-api/) for a full runnable example with multiple agents and grants.
## Expected Output Shape

The full example prints:

```text
KAI OS Kotlin Runtime API demo
run=run-... workflow=kotlin-runtime-api-review success=true
scheduler backend=local-worker priority=true recovery=true triggers=1

PID  AGENT       STATE      TOKENS  MEM  SYSCALLS  TOOL_MS  COST  WORKER
1    inspector   SUCCEEDED  ...     ...  1         ...      0     local-worker-...
2    researcher  SUCCEEDED  ...     ...  1         ...      0     local-worker-...
3    reviewer    SUCCEEDED  ...     ...  1         ...      0     local-worker-...
4    validator   SUCCEEDED  ...     ...  1         ...      0     local-worker-...

SYSCALL LEDGER
sys-... pid=... agent=... tool=... allowed=true args=...
```

## Trust Path

The repository smoke check runs this example and asserts that the output includes `success=true` and `SYSCALL LEDGER`:

```bash
./scripts/repository-ci-smoke.sh
```

Use the CLI-first path when you want artifacts, trace files, capsules, and review output on disk. Use the Kotlin API path when you want to embed the runtime model inside a JVM application.
