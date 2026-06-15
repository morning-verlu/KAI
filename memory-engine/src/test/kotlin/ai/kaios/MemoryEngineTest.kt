package ai.kaios

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-13T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `session memory appends reads and clears entries by run`() {
        val store = SessionMemoryStore()
        val runId = RunId("run-memory")
        val agent = AgentId("planner")

        store.append(MemoryEntry(runId, agent, "user", "task", clock.instant()))
        store.append(MemoryEntry(RunId("run-other"), agent, "user", "other", clock.instant()))

        assertEquals(listOf("task"), store.read(runId, agent).map { it.content })

        store.clear(runId)
        assertTrue(store.read(runId, agent).isEmpty())
        assertEquals(listOf("other"), store.read(RunId("run-other"), agent).map { it.content })
    }

    @Test
    fun `session memory can isolate entries by scope`() {
        val store = SessionMemoryStore()
        val runId = RunId("run-scope")
        val agent = AgentId("agent")

        store.append(MemoryEntry(runId, agent, "user", "first", clock.instant(), scopeId = "pid-1"))
        store.append(MemoryEntry(runId, agent, "user", "second", clock.instant().plusSeconds(1), scopeId = "pid-2"))

        assertEquals(listOf("first", "second"), store.read(runId, agent).map { it.content })
        assertEquals(listOf("second"), store.read(runId, agent, scopeId = "pid-2").map { it.content })
    }

    @Test
    fun `sqlite memory persists entries across store instances and filters by agent`() {
        val database = Files.createTempDirectory("kaios-sqlite-memory").resolve("memory.db")
        val runId = RunId("run-sqlite")
        val planner = AgentId("planner")
        val executor = AgentId("executor")

        SQLiteMemoryStore(database).apply {
            append(MemoryEntry(runId, planner, "user", "plan input", clock.instant()))
            append(MemoryEntry(runId, executor, "assistant", "execution output", clock.instant().plusSeconds(1)))
            append(MemoryEntry(RunId("run-other"), planner, "user", "other", clock.instant().plusSeconds(2)))
        }

        val reopened = SQLiteMemoryStore(database)

        assertEquals(
            listOf("plan input", "execution output"),
            reopened.read(runId).map { it.content },
        )
        assertEquals(listOf("plan input"), reopened.read(runId, planner).map { it.content })
        assertEquals(listOf("execution output"), reopened.read(runId, executor).map { it.content })
    }

    @Test
    fun `sqlite memory clear removes one run without deleting other runs`() {
        val database = Files.createTempDirectory("kaios-sqlite-clear").resolve("memory.db")
        val store = SQLiteMemoryStore(database)
        val agent = AgentId("planner")

        store.append(MemoryEntry(RunId("run-a"), agent, "user", "a", clock.instant()))
        store.append(MemoryEntry(RunId("run-b"), agent, "user", "b", clock.instant()))

        store.clear(RunId("run-a"))

        assertTrue(store.read(RunId("run-a")).isEmpty())
        assertEquals(listOf("b"), store.read(RunId("run-b")).map { it.content })
    }

    @Test
    fun `snapshot store writes and reads workflow result JSON`() {
        val runtime = AgentRuntime(clock)
        val runId = RunId("run-snapshot")
        val process = runtime.spawn(AgentSpec(AgentId("validator")), runId)
        runtime.start(process.pid)
        runtime.succeed(process.pid, TokenUsage(input = 2, output = 3), contextSize = 64)

        val result = WorkflowResult(
            runId = runId,
            workflowName = "snapshot",
            success = true,
            outputs = emptyMap(),
            finalOutput = "ok",
            processes = runtime.processes(runId),
            events = runtime.events(runId),
        )

        val root = Files.createTempDirectory("kaios-runs")
        val store = FileRunSnapshotStore(root)
        val path = store.save("snapshot task", result)
        val loaded = store.load(runId)

        assertTrue(Files.exists(path))
        assertEquals("run-snapshot", loaded.runId)
        assertEquals("snapshot task", loaded.task)
        assertEquals("ok", loaded.finalOutput)
        assertEquals(5, loaded.processes.single().tokens)
        assertEquals("SUCCEEDED", loaded.processes.single().state)
    }

    @Test
    fun `snapshot store lists saved run snapshots`() {
        val root = Files.createTempDirectory("kaios-runs-list")
        val store = FileRunSnapshotStore(root)
        val runtime = AgentRuntime(clock)

        listOf("run-list-a", "run-list-b").forEachIndexed { index, value ->
            val runId = RunId(value)
            val process = runtime.spawn(AgentSpec(AgentId("planner")), runId)
            runtime.start(process.pid)
            runtime.succeed(process.pid, TokenUsage(input = 1, output = 2), contextSize = 10)
            val path = store.save(
                task = "task $value",
                result = WorkflowResult(
                    runId = runId,
                    workflowName = "default",
                    success = true,
                    outputs = emptyMap(),
                    finalOutput = "ok",
                    processes = runtime.processes(runId),
                    events = runtime.events(runId),
                ),
            )
            Files.setLastModifiedTime(path, FileTime.fromMillis((index + 1) * 1_000L))
        }

        val listed = store.list()

        assertEquals(listOf("run-list-b", "run-list-a"), listed.map { it.runId })
    }
}
