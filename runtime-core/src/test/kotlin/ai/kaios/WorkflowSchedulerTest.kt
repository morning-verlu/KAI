package ai.kaios

import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkflowSchedulerTest {
    @Test
    fun `scheduler runs ready nodes in a parallel coroutine batch`() {
        var largestReadyBatch = 0
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = MockModelProvider(),
            onReadyBatch = { size -> largestReadyBatch = maxOf(largestReadyBatch, size) },
        )

        val result = scheduler.run(
            Workflow(
                name = "parallel",
                nodes = listOf(
                    WorkflowNode("alpha", AgentSpec(AgentId("alpha"))),
                    WorkflowNode("beta", AgentSpec(AgentId("beta"))),
                ),
            ),
            input = "parallel work",
            runId = RunId("run-parallel"),
        )

        assertTrue(result.success)
        assertEquals(2, largestReadyBatch)
        assertEquals(setOf("alpha", "beta"), result.outputs.keys)
    }

    @Test
    fun `node failure propagates when no fallback exists`() {
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = FailingModelProvider("primary"),
        )

        val result = scheduler.run(
            Workflow(
                name = "failure",
                nodes = listOf(WorkflowNode("primary", AgentSpec(AgentId("primary")))),
            ),
            input = "fail",
            runId = RunId("run-failure"),
        )

        assertFalse(result.success)
        assertTrue(result.finalOutput.contains("planned failure"))
        assertEquals(ProcessState.FAILED, result.processes.single().state)
    }

    @Test
    fun `fallback node can recover a failed node and unblock dependents`() {
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = FailingModelProvider("primary", delegate = MockModelProvider()),
        )

        val result = scheduler.run(
            Workflow(
                name = "fallback",
                nodes = listOf(
                    WorkflowNode(
                        id = "primary",
                        agent = AgentSpec(AgentId("primary")),
                        fallback = "backup",
                    ),
                    WorkflowNode(
                        id = "backup",
                        agent = AgentSpec(AgentId("backup")),
                        fallbackOnly = true,
                    ),
                    WorkflowNode(
                        id = "consumer",
                        agent = AgentSpec(AgentId("consumer")),
                        dependencies = setOf("primary"),
                    ),
                ),
            ),
            input = "recover",
            runId = RunId("run-fallback"),
        )

        assertTrue(result.success)
        assertEquals("backup", result.outputs.getValue("primary").fallbackNodeId)
        assertEquals(ProcessState.FAILED, result.processes.first { it.agent.value == "primary" }.state)
        assertEquals(ProcessState.SUCCEEDED, result.processes.first { it.agent.value == "backup" }.state)
        assertEquals(ProcessState.SUCCEEDED, result.processes.first { it.agent.value == "consumer" }.state)
    }

    @Test
    fun `node retry records failed attempt and succeeds on later attempt`() {
        val provider = FlakyModelProvider("worker", failuresBeforeSuccess = 1)
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
        )

        val result = scheduler.run(
            Workflow(
                name = "retry",
                nodes = listOf(
                    WorkflowNode(
                        id = "worker",
                        agent = AgentSpec(AgentId("worker")),
                        maxAttempts = 2,
                    ),
                ),
            ),
            input = "retry transient work",
            runId = RunId("run-retry"),
        )

        assertTrue(result.success)
        assertEquals(2, provider.calls)
        assertEquals(listOf(ProcessState.FAILED, ProcessState.SUCCEEDED), result.processes.map { it.state })
        assertTrue(result.events.any { it.type == RuntimeEventType.RETRYING && it.message.contains("2/2") })
    }

    @Test
    fun `node retry exhausts attempts and reports final failure`() {
        val provider = FlakyModelProvider("worker", failuresBeforeSuccess = 3)
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
        )

        val result = scheduler.run(
            Workflow(
                name = "retry-failure",
                nodes = listOf(
                    WorkflowNode(
                        id = "worker",
                        agent = AgentSpec(AgentId("worker")),
                        maxAttempts = 2,
                    ),
                ),
            ),
            input = "retry transient work",
            runId = RunId("run-retry-failure"),
        )

        assertFalse(result.success)
        assertEquals(2, provider.calls)
        assertEquals(listOf(ProcessState.FAILED, ProcessState.FAILED), result.processes.map { it.state })
        assertTrue(result.finalOutput.contains("transient failure 2"))
    }

    @Test
    fun `node timeout is recorded as a cancelled process`() {
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = SleepingModelProvider(),
            nodeTimeout = Duration.ofMillis(100),
        )

        val result = scheduler.run(
            Workflow(
                name = "timeout",
                nodes = listOf(WorkflowNode("slow", AgentSpec(AgentId("slow")))),
            ),
            input = "timeout",
            runId = RunId("run-timeout"),
        )

        assertFalse(result.success)
        assertEquals(ProcessState.CANCELLED, result.processes.single().state)
        assertTrue(result.events.any { it.type == RuntimeEventType.CANCELLED })
    }

    @Test
    fun `failure cancels running sibling node jobs`() {
        val slowStarted = CountDownLatch(1)
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = FailingAfterSlowStartsModelProvider(slowStarted),
        )

        val result = scheduler.run(
            Workflow(
                name = "cancel-siblings",
                nodes = listOf(
                    WorkflowNode("fail", AgentSpec(AgentId("fail"))),
                    WorkflowNode("slow", AgentSpec(AgentId("slow"))),
                ),
            ),
            input = "cancel",
            runId = RunId("run-cancel-sibling"),
        )

        assertFalse(result.success)
        assertEquals(ProcessState.FAILED, result.processes.first { it.agent.value == "fail" }.state)
        assertEquals(ProcessState.CANCELLED, result.processes.first { it.agent.value == "slow" }.state)
    }

    @Test
    fun `ready nodes run in priority order with local worker backend`() {
        val provider = RecordingModelProvider()
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
            executorBackend = LocalWorkerExecutorBackend(parallelism = 1),
        )

        val result = scheduler.run(
            Workflow(
                name = "priority",
                nodes = listOf(
                    WorkflowNode("low", AgentSpec(AgentId("low")), priority = 0),
                    WorkflowNode("high", AgentSpec(AgentId("high")), priority = 10),
                ),
            ),
            input = "priority",
            runId = RunId("run-priority"),
        )

        assertTrue(result.success)
        assertEquals(listOf("high", "low"), provider.calls)
        assertEquals("local-worker", result.scheduler.executorBackend)
        assertTrue(result.scheduler.priorityEnabled)
        assertTrue(result.processes.all { process -> process.workerId?.startsWith("local-worker-") == true })
    }

    @Test
    fun `runtime crash can recover with a new process id`() {
        val provider = CrashingThenRecoveringProvider()
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
        )

        val result = scheduler.run(
            Workflow(
                name = "recovery",
                nodes = listOf(
                    WorkflowNode(
                        id = "worker",
                        agent = AgentSpec(AgentId("worker")),
                        recoveryPolicy = ProcessRecoveryPolicy(maxRestarts = 1, memoryIsolation = MemoryIsolation.PROCESS),
                    ),
                ),
            ),
            input = "recover crash",
            runId = RunId("run-recovery"),
        )

        assertTrue(result.success)
        assertEquals(listOf(ProcessState.FAILED, ProcessState.SUCCEEDED), result.processes.map { it.state })
        assertEquals(ProcessFailureKind.RUNTIME_CRASH, result.processes.first().failureKind)
        assertEquals(result.processes.first().pid, result.processes.last().recoveryOfPid)
        assertTrue(result.events.any { it.type == RuntimeEventType.RECOVERING })
        assertTrue(result.events.any { it.type == RuntimeEventType.RECOVERED })
        assertTrue(result.scheduler.recoveryEnabled)
    }

    @Test
    fun `process memory isolation does not read previous attempt memory`() {
        val provider = MemoryRecordingFlakyProvider()
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
            memory = TestMemoryStore(),
        )

        val result = scheduler.run(
            Workflow(
                name = "process-memory",
                nodes = listOf(
                    WorkflowNode(
                        id = "worker",
                        agent = AgentSpec(
                            id = AgentId("worker"),
                            memoryIsolation = MemoryIsolation.PROCESS,
                        ),
                        maxAttempts = 2,
                    ),
                ),
            ),
            input = "memory retry",
            runId = RunId("run-process-memory"),
        )

        assertTrue(result.success)
        assertEquals(listOf(1, 1), provider.memorySizes)
        assertEquals(2, result.processes.mapNotNull { it.memoryScopeId }.distinct().size)
    }

    @Test
    fun `event trigger waits for matching runtime event`() {
        val provider = RecordingModelProvider()
        val scheduler = WorkflowScheduler(
            runtime = AgentRuntime(),
            modelProvider = provider,
        )

        val result = scheduler.run(
            Workflow(
                name = "triggered",
                nodes = listOf(
                    WorkflowNode("starter", AgentSpec(AgentId("starter"))),
                    WorkflowNode(
                        id = "watcher",
                        agent = AgentSpec(AgentId("watcher")),
                        triggers = listOf(WorkflowTrigger(RuntimeEventType.SUCCEEDED, nodeId = "starter")),
                    ),
                ),
            ),
            input = "trigger",
            runId = RunId("run-trigger"),
        )

        assertTrue(result.success)
        assertEquals(listOf("starter", "watcher"), provider.calls)
        assertEquals(1, result.scheduler.triggerCount)
    }

    private class FailingModelProvider(
        private val failingAgent: String,
        private val delegate: ModelProvider = MockModelProvider(),
    ) : ModelProvider {
        override fun complete(request: ModelRequest): ModelResponse {
            if (request.agent.id.value == failingAgent) {
                error("planned failure from ${request.agent.id.value}")
            }
            return delegate.complete(request)
        }
    }

    private class SleepingModelProvider : ModelProvider {
        override fun complete(request: ModelRequest): ModelResponse {
            try {
                Thread.sleep(5_000)
            } catch (error: InterruptedException) {
                throw CancellationException("sleeping model cancelled")
            }
            return ModelResponse("slow output", TokenUsage(input = 1, output = 1))
        }
    }

    private class FlakyModelProvider(
        private val flakyAgent: String,
        private val failuresBeforeSuccess: Int,
    ) : ModelProvider {
        var calls: Int = 0

        override fun complete(request: ModelRequest): ModelResponse {
            if (request.agent.id.value == flakyAgent) {
                calls += 1
                if (calls <= failuresBeforeSuccess) {
                    error("transient failure $calls")
                }
            }
            return ModelResponse("recovered on attempt $calls", TokenUsage(input = 1, output = 1))
        }
    }

    private class FailingAfterSlowStartsModelProvider(
        private val slowStarted: CountDownLatch,
    ) : ModelProvider {
        override fun complete(request: ModelRequest): ModelResponse {
            return when (request.agent.id.value) {
                "fail" -> {
                    slowStarted.await(1, TimeUnit.SECONDS)
                    error("planned sibling failure")
                }
                "slow" -> {
                    slowStarted.countDown()
                    try {
                        Thread.sleep(5_000)
                    } catch (error: InterruptedException) {
                        throw CancellationException("slow sibling cancelled")
                    }
                    ModelResponse("slow output", TokenUsage(input = 1, output = 1))
                }
                else -> ModelResponse("ok", TokenUsage(input = 1, output = 1))
            }
        }
    }

    private class RecordingModelProvider : ModelProvider {
        val calls = mutableListOf<String>()

        override fun complete(request: ModelRequest): ModelResponse {
            calls += request.agent.id.value
            return ModelResponse("ok ${request.agent.id.value}", TokenUsage(input = 1, output = 1))
        }
    }

    private class CrashingThenRecoveringProvider : ModelProvider {
        private var calls = 0

        override fun complete(request: ModelRequest): ModelResponse {
            calls += 1
            if (calls == 1) {
                throw RuntimeCrashException("simulated runtime crash")
            }
            return ModelResponse("recovered", TokenUsage(input = 1, output = 1))
        }
    }

    private class MemoryRecordingFlakyProvider : ModelProvider {
        val memorySizes = mutableListOf<Int>()
        private var calls = 0

        override fun complete(request: ModelRequest): ModelResponse {
            calls += 1
            memorySizes += request.memory.size
            if (calls == 1) {
                error("first attempt failed")
            }
            return ModelResponse("second attempt succeeded", TokenUsage(input = 1, output = 1))
        }
    }

    private class TestMemoryStore : MemoryStore {
        private val entries = mutableListOf<MemoryEntry>()

        override fun append(entry: MemoryEntry) {
            entries += entry
        }

        override fun read(runId: RunId, agent: AgentId?, scopeId: String?): List<MemoryEntry> =
            entries.filter { entry ->
                entry.runId == runId &&
                    (agent == null || entry.agent == agent) &&
                    (scopeId == null || entry.scopeId == scopeId)
            }

        override fun clear(runId: RunId) {
            entries.removeAll { entry -> entry.runId == runId }
        }
    }
}
