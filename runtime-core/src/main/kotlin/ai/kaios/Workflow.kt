package ai.kaios

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

data class Workflow(
    val name: String,
    val nodes: List<WorkflowNode>,
) {
    init {
        require(name.isNotBlank()) { "Workflow name cannot be blank." }
        require(nodes.isNotEmpty()) { "Workflow must contain at least one node." }

        val ids = nodes.map { it.id }
        require(ids.size == ids.toSet().size) { "Workflow node ids must be unique." }

        val known = ids.toSet()
        nodes.forEach { node ->
            require(node.dependencies.all { it in known }) { "Node '${node.id}' depends on an unknown node." }
            node.fallback?.let { require(it in known) { "Node '${node.id}' references an unknown fallback node '$it'." } }
        }
    }
}

data class WorkflowNode(
    val id: String,
    val agent: AgentSpec,
    val dependencies: Set<String> = emptySet(),
    val fallback: String? = null,
    val fallbackOnly: Boolean = false,
    val maxAttempts: Int = 1,
    val retryBackoff: Duration = Duration.ZERO,
    val priority: Int = 0,
    val recoveryPolicy: ProcessRecoveryPolicy = ProcessRecoveryPolicy(),
    val triggers: List<WorkflowTrigger> = emptyList(),
    val executorHint: String? = null,
) {
    init {
        require(maxAttempts >= 1) { "Workflow node '$id' must have at least one attempt." }
        require(!retryBackoff.isNegative) { "Workflow node '$id' retry backoff cannot be negative." }
        require(executorHint == null || executorHint.isNotBlank()) { "Workflow node '$id' executorHint cannot be blank." }
    }
}

data class ProcessRecoveryPolicy(
    val maxRestarts: Int = 0,
    val backoff: Duration = Duration.ZERO,
    val memoryIsolation: MemoryIsolation = MemoryIsolation.AGENT,
) {
    init {
        require(maxRestarts >= 0) { "Process recovery maxRestarts cannot be negative." }
        require(!backoff.isNegative) { "Process recovery backoff cannot be negative." }
    }
}

data class WorkflowTrigger(
    val eventType: RuntimeEventType,
    val agent: AgentId? = null,
    val nodeId: String? = null,
)

data class ExecutorLease(
    val workerId: String,
)

interface ExecutorBackend {
    val id: String

    suspend fun <T> execute(node: WorkflowNode, block: suspend (ExecutorLease) -> T): T
}

object InProcessExecutorBackend : ExecutorBackend {
    override val id: String = "in-process"

    override suspend fun <T> execute(node: WorkflowNode, block: suspend (ExecutorLease) -> T): T =
        block(ExecutorLease(node.executorHint ?: "in-process"))
}

class LocalWorkerExecutorBackend(
    parallelism: Int,
) : ExecutorBackend {
    override val id: String = "local-worker"

    private val permits = Semaphore(parallelism)
    private val nextWorker = AtomicLong(1)

    init {
        require(parallelism >= 1) { "Local worker parallelism must be at least 1." }
    }

    override suspend fun <T> execute(node: WorkflowNode, block: suspend (ExecutorLease) -> T): T =
        permits.withPermit {
            val workerId = node.executorHint ?: "local-worker-${nextWorker.getAndIncrement()}"
            block(ExecutorLease(workerId))
        }
}

class RuntimeCrashException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

data class NodeResult(
    val nodeId: String,
    val agent: AgentId,
    val pid: ProcessId,
    val output: String,
    val success: Boolean,
    val error: String? = null,
    val fallbackNodeId: String? = null,
)

data class WorkflowResult(
    val runId: RunId,
    val workflowName: String,
    val success: Boolean,
    val outputs: Map<String, NodeResult>,
    val finalOutput: String,
    val processes: List<AgentProcess>,
    val events: List<RuntimeEvent>,
    val scheduler: SchedulerEvidence = SchedulerEvidence(),
    val syscalls: List<ToolExecutionRecord> = emptyList(),
)

data class SchedulerEvidence(
    val executorBackend: String = "in-process",
    val priorityEnabled: Boolean = false,
    val triggerCount: Int = 0,
    val recoveryEnabled: Boolean = false,
)

class WorkflowScheduler(
    private val runtime: AgentRuntime,
    private val modelProvider: ModelProvider,
    private val tools: ToolRegistry = ToolRegistry.Empty,
    private val memory: MemoryStore = NoopMemoryStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nodeTimeout: Duration? = null,
    private val executorBackend: ExecutorBackend = InProcessExecutorBackend,
    private val onReadyBatch: (Int) -> Unit = {},
) {
    fun run(workflow: Workflow, input: String, runId: RunId = RunId.new()): WorkflowResult =
        runBlocking {
            runSuspend(workflow, input, runId)
        }

    suspend fun runSuspend(workflow: Workflow, input: String, runId: RunId = RunId.new()): WorkflowResult {
        val nodesById = workflow.nodes.associateBy { it.id }
        val pending = workflow.nodes.filterNot { it.fallbackOnly }.mapTo(linkedSetOf()) { it.id }
        val completed = linkedMapOf<String, NodeResult>()
        var success = true
        var failureOutput = ""

        while (pending.isNotEmpty() && success) {
            val ready = pending
                .mapIndexed { index, id -> index to nodesById.getValue(id) }
                .filter { (_, node) -> node.dependencies.all { it in completed } }
                .filter { (_, node) -> node.triggers.isEmpty() || node.triggers.any { trigger -> trigger.matches(runtime.events(runId), nodesById) } }
                .sortedWith(
                    compareByDescending<Pair<Int, WorkflowNode>> { (_, node) -> node.priority }
                        .thenBy { (index, _) -> index },
                )
                .map { (_, node) -> node }

            if (ready.isEmpty()) {
                success = false
                failureOutput = "Workflow '${workflow.name}' has unresolved dependencies or a cycle."
                break
            }

            onReadyBatch(ready.size)
            val batchResults = executeReadyBatch(ready, nodesById, input, completed.toMap(), runId)

            for ((node, result) in batchResults) {
                if (!result.success) {
                    success = false
                    failureOutput = result.error ?: "Node '${node.id}' failed."
                    break
                }

                completed[node.id] = result
                pending.remove(node.id)
            }
        }

        val finalOutput = when {
            success && completed.isNotEmpty() -> completed.values.last().output
            failureOutput.isNotBlank() -> failureOutput
            else -> "Workflow '${workflow.name}' did not produce output."
        }

        return WorkflowResult(
            runId = runId,
            workflowName = workflow.name,
            success = success,
            outputs = completed.toMap(),
            finalOutput = finalOutput,
            processes = runtime.processes(runId),
            events = runtime.events(runId),
            scheduler = SchedulerEvidence(
                executorBackend = executorBackend.id,
                priorityEnabled = workflow.nodes.any { it.priority != 0 },
                triggerCount = workflow.nodes.sumOf { it.triggers.size },
                recoveryEnabled = workflow.nodes.any { it.recoveryPolicy.maxRestarts > 0 },
            ),
            syscalls = tools.auditRecords(runId),
        )
    }

    private suspend fun executeReadyBatch(
        ready: List<WorkflowNode>,
        nodesById: Map<String, WorkflowNode>,
        input: String,
        completed: Map<String, NodeResult>,
        runId: RunId,
    ): Map<WorkflowNode, NodeResult> = supervisorScope {
        val deferreds: Map<WorkflowNode, Deferred<NodeResult>> = ready.associateWith { node ->
            async {
                executeNodeWithPolicy(node, input, completed, runId)
            }
        }
        val results = linkedMapOf<WorkflowNode, NodeResult>()

        for ((node, deferred) in deferreds) {
            val result = try {
                deferred.await()
            } catch (error: Throwable) {
                val fallback = node.fallback
                if (fallback == null) {
                    val message = error.message ?: "Node '${node.id}' failed."
                    cancelRemaining(deferreds, deferred)
                    NodeResult(
                        nodeId = node.id,
                        agent = node.agent.id,
                        pid = ProcessId(1),
                        output = "",
                        success = false,
                        error = message,
                    )
                } else {
                    val fallbackNode = nodesById.getValue(fallback)
                    val fallbackResult = executeNodeWithPolicy(
                        node = fallbackNode,
                        input = "$input\nfallback from ${node.id}: ${error.message}",
                        completed = completed,
                        runId = runId,
                    )
                    fallbackResult.copy(nodeId = node.id, fallbackNodeId = fallback)
                }
            }

            results[node] = result
            if (!result.success) break
        }

        results
    }

    private suspend fun executeNodeWithPolicy(
        node: WorkflowNode,
        input: String,
        completed: Map<String, NodeResult>,
        runId: RunId,
    ): NodeResult {
        var attempt = 1
        var restart = 0
        var recoveryOfPid: ProcessId? = null
        while (true) {
            var activePid: ProcessId? = null
            val block: suspend () -> NodeResult = {
                executorBackend.execute(node) { lease ->
                    runInterruptible(dispatcher) {
                        executeNode(
                            node = node,
                            input = input,
                            completed = completed,
                            runId = runId,
                            attempt = attempt,
                            recoveryOfPid = recoveryOfPid,
                            workerId = lease.workerId,
                            onSpawn = { pid -> activePid = pid },
                        )
                    }
                }
            }

            try {
                val result = if (nodeTimeout == null) {
                    block()
                } else {
                    withTimeout(nodeTimeout.toMillis()) {
                        block()
                    }
                }
                recoveryOfPid?.let { runtime.recordRecovered(it, result.pid) }
                return result
            } catch (error: TimeoutCancellationException) {
                activePid?.let { pid ->
                    if (runtime.process(pid)?.state?.isTerminal == false) {
                        runtime.cancel(pid, ProcessFailureKind.TIMEOUT, "timeout after ${nodeTimeout?.toMillis()}ms")
                    }
                }
                throw NodeExecutionException(
                    pid = activePid ?: ProcessId(1),
                    failureKind = ProcessFailureKind.TIMEOUT,
                    message = "Node '${node.id}' timed out after ${nodeTimeout?.toMillis()}ms.",
                    cause = error,
                )
            } catch (error: CancellationException) {
                activePid?.let { pid ->
                    if (runtime.process(pid)?.state?.isTerminal == false) {
                        runtime.cancel(pid)
                    }
                }
                throw error
            } catch (error: NodeExecutionException) {
                if (attempt < node.maxAttempts) {
                    val nextAttempt = attempt + 1
                    runtime.recordRetry(error.pid, nextAttempt, node.maxAttempts, error.message ?: "node failed")
                    if (!node.retryBackoff.isZero) {
                        delay(node.retryBackoff.toMillis())
                    }
                    attempt = nextAttempt
                    recoveryOfPid = null
                    continue
                }
                val policy = node.recoveryPolicy
                if (error.failureKind == ProcessFailureKind.RUNTIME_CRASH && restart < policy.maxRestarts) {
                    val nextRestart = restart + 1
                    runtime.recordRecovering(error.pid, nextRestart, policy.maxRestarts, error.message ?: "node crashed")
                    if (!policy.backoff.isZero) {
                        delay(policy.backoff.toMillis())
                    }
                    restart = nextRestart
                    attempt += 1
                    recoveryOfPid = error.pid
                    continue
                }
                throw error
            }
        }
    }

    private suspend fun cancelRemaining(
        deferreds: Map<WorkflowNode, Deferred<NodeResult>>,
        completedDeferred: Deferred<NodeResult>,
    ) {
        deferreds.values
            .filter { deferred -> deferred !== completedDeferred && !deferred.isCompleted }
            .forEach { deferred -> deferred.cancelAndJoin() }
    }

    private fun executeNode(
        node: WorkflowNode,
        input: String,
        completed: Map<String, NodeResult>,
        runId: RunId,
        attempt: Int,
        recoveryOfPid: ProcessId?,
        workerId: String,
        onSpawn: (ProcessId) -> Unit,
    ): NodeResult {
        val memoryIsolation = effectiveMemoryIsolation(node)
        val process = runtime.spawn(
            agent = node.agent.copy(memoryIsolation = memoryIsolation),
            runId = runId,
            attempt = attempt,
            recoveryOfPid = recoveryOfPid,
            memoryScopeId = memoryScopeId(runId, node.agent, memoryIsolation, attempt),
            workerId = workerId,
        )
        onSpawn(process.pid)
        runtime.start(process.pid)

        return try {
            appendMemory(process.pid, runId, node.agent, process.memoryScopeId, "user", input)

            val dependencyContext = node.dependencies.associateWith { dependency ->
                completed[dependency]?.output.orEmpty()
            }
            val history = if (node.agent.memoryEnabled) {
                memory.read(runId, node.agent.id, process.memoryScopeId)
            } else {
                emptyList()
            }

            val response = modelProvider.complete(
                ModelRequest(
                    runId = runId,
                    agent = node.agent,
                    input = input,
                    dependencyContext = dependencyContext,
                    memory = history,
                    availableTools = node.agent.allowedTools.intersect(tools.names),
                ),
            )

            val toolResults = response.toolCalls.map { call ->
                tools.execute(
                    node.agent,
                    call,
                    ToolExecutionContext(runId = runId, pid = process.pid, agent = node.agent.id),
                ).also { result -> runtime.recordSyscall(process.pid, result) }
            }

            val failedTool = toolResults.firstOrNull { !it.ok }
            if (failedTool != null) {
                throw ToolExecutionException(
                    failedTool.error ?: "Tool '${failedTool.tool}' failed.",
                    if (failedTool.denied) ProcessFailureKind.TOOL_DENIED else ProcessFailureKind.AGENT_ERROR,
                )
            }

            val output = buildString {
                append(response.content)
                if (toolResults.isNotEmpty()) {
                    appendLine()
                    toolResults.forEach { result -> appendLine("syscall ${result.tool}: ${result.output}") }
                }
            }.trim()

            appendMemory(process.pid, runId, node.agent, process.memoryScopeId, "assistant", output)

            val contextSize = history.sumOf { it.content.length } +
                input.length +
                dependencyContext.values.sumOf { it.length } +
                output.length

            runtime.succeed(process.pid, response.tokenUsage, contextSize)

            NodeResult(
                nodeId = node.id,
                agent = node.agent.id,
                pid = process.pid,
                output = output,
                success = true,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val message = error.message ?: "Node '${node.id}' failed."
            val failureKind = when (error) {
                is RuntimeCrashException -> ProcessFailureKind.RUNTIME_CRASH
                is ToolExecutionException -> error.failureKind
                else -> ProcessFailureKind.AGENT_ERROR
            }
            if (failureKind == ProcessFailureKind.RUNTIME_CRASH) {
                runtime.crash(process.pid, message)
            } else {
                runtime.fail(process.pid, message, failureKind)
            }
            throw NodeExecutionException(process.pid, failureKind, message, error)
        }
    }

    private fun appendMemory(pid: ProcessId, runId: RunId, agent: AgentSpec, scopeId: String?, role: String, content: String) {
        if (!agent.memoryEnabled) return

        val entry = MemoryEntry(
            runId = runId,
            agent = agent.id,
            role = role,
            content = content,
            timestamp = java.time.Instant.now(),
            scopeId = scopeId,
        )
        memory.append(entry)
        runtime.recordMemory(pid, entry)
    }
}

private fun memoryScopeId(runId: RunId, agent: AgentSpec, isolation: MemoryIsolation, attempt: Int): String =
    when (isolation) {
        MemoryIsolation.AGENT -> "${runId.value}:${agent.id.value}"
        MemoryIsolation.WORKFLOW -> runId.value
        MemoryIsolation.PROCESS -> "${runId.value}:${agent.id.value}:attempt-$attempt"
    }

private fun effectiveMemoryIsolation(node: WorkflowNode): MemoryIsolation =
    if (node.recoveryPolicy.maxRestarts > 0 || node.recoveryPolicy.memoryIsolation != MemoryIsolation.AGENT) {
        node.recoveryPolicy.memoryIsolation
    } else {
        node.agent.memoryIsolation
    }

private fun WorkflowTrigger.matches(events: List<RuntimeEvent>, nodesById: Map<String, WorkflowNode>): Boolean =
    events.any { event ->
        event.type == eventType &&
            (agent == null || event.agent == agent) &&
            (nodeId == null || nodesById[nodeId]?.agent?.id == event.agent)
    }

private class ToolExecutionException(
    message: String,
    val failureKind: ProcessFailureKind,
) : RuntimeException(message)

private class NodeExecutionException(
    val pid: ProcessId,
    val failureKind: ProcessFailureKind,
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
