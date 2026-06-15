package ai.kaios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AgentRuntimeTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-13T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `lifecycle transitions update process metrics and events`() {
        val runtime = AgentRuntime(clock)
        val runId = RunId("run-runtime")
        val process = runtime.spawn(AgentSpec(AgentId("planner")), runId)

        runtime.start(process.pid)
        runtime.recordSyscall(process.pid, ToolResult.success("echo", "ok"))
        runtime.recordMemory(
            process.pid,
            MemoryEntry(runId, AgentId("planner"), "assistant", "done", clock.instant()),
        )
        runtime.succeed(process.pid, TokenUsage(input = 3, output = 5), contextSize = 42)

        val completed = runtime.process(process.pid)
        assertEquals(ProcessState.SUCCEEDED, completed?.state)
        assertEquals(8, completed?.tokenUsage?.total)
        assertEquals(42, completed?.contextSize)
        assertEquals(1, completed?.syscallCount)
        assertEquals(
            listOf(
                RuntimeEventType.SPAWNED,
                RuntimeEventType.STARTED,
                RuntimeEventType.TOOL_CALLED,
                RuntimeEventType.MEMORY_APPENDED,
                RuntimeEventType.SUCCEEDED,
            ),
            runtime.events(runId).map { it.type },
        )
    }

    @Test
    fun `invalid lifecycle transitions are rejected`() {
        val runtime = AgentRuntime(clock)
        val process = runtime.spawn(AgentSpec(AgentId("executor")), RunId("run-invalid"))

        assertFailsWith<IllegalStateException> {
            runtime.resume(process.pid)
        }

        runtime.start(process.pid)
        runtime.succeed(process.pid, TokenUsage(input = 1, output = 1), contextSize = 10)

        assertFailsWith<IllegalStateException> {
            runtime.cancel(process.pid)
        }
    }

    @Test
    fun `crash records runtime failure kind and crashed event`() {
        val runtime = AgentRuntime(clock)
        val runId = RunId("run-crash")
        val process = runtime.spawn(AgentSpec(AgentId("worker")), runId)

        runtime.start(process.pid)
        runtime.crash(process.pid, "host process crashed")

        val failed = runtime.process(process.pid)
        assertEquals(ProcessState.FAILED, failed?.state)
        assertEquals(ProcessFailureKind.RUNTIME_CRASH, failed?.failureKind)
        assertEquals(
            listOf(
                RuntimeEventType.SPAWNED,
                RuntimeEventType.STARTED,
                RuntimeEventType.CRASHED,
                RuntimeEventType.FAILED,
            ),
            runtime.events(runId).map { it.type },
        )
    }

    @Test
    fun `syscall metrics track denied calls tool time and estimated cost`() {
        val runtime = AgentRuntime(clock)
        val process = runtime.spawn(AgentSpec(AgentId("agent")), RunId("run-cost"))

        runtime.start(process.pid)
        runtime.recordSyscall(
            process.pid,
            ToolResult.failure("echo", "denied by capability").copy(
                durationMillis = 7,
                estimatedCostMicros = 11,
                denied = true,
            ),
        )

        val updated = runtime.process(process.pid)
        assertEquals(1, updated?.syscallCount)
        assertEquals(1, updated?.deniedSyscallCount)
        assertEquals(7, updated?.toolTimeMillis)
        assertEquals(11, updated?.estimatedCostMicros)
        assertEquals(RuntimeEventType.SYSCALL_DENIED, runtime.events(RunId("run-cost")).map { it.type }.dropLast(1).last())
        assertEquals(RuntimeEventType.COST_RECORDED, runtime.events(RunId("run-cost")).last().type)
    }
}
