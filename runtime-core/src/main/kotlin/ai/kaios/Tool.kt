package ai.kaios

import java.util.concurrent.atomic.AtomicLong

enum class ToolPermission {
    ECHO,
    READ_CLOCK,
    NETWORK,
    FILE,
}

data class ToolCapabilityLimits(
    val maxCalls: Int? = null,
    val maxMillis: Long? = null,
) {
    init {
        require(maxCalls == null || maxCalls >= 0) { "maxCalls cannot be negative." }
        require(maxMillis == null || maxMillis >= 0) { "maxMillis cannot be negative." }
    }
}

data class ToolCostProfile(
    val estimatedMicros: Long = 0,
) {
    init {
        require(estimatedMicros >= 0) { "estimatedMicros cannot be negative." }
    }
}

data class ToolCapabilityGrant(
    val tool: String,
    val permission: ToolPermission,
    val scope: String = "*",
    val limits: ToolCapabilityLimits = ToolCapabilityLimits(),
    val cost: ToolCostProfile = ToolCostProfile(),
) {
    init {
        require(tool.isNotBlank()) { "Tool capability grant tool cannot be blank." }
        require(scope.isNotBlank()) { "Tool capability grant scope cannot be blank." }
    }
}

data class ToolCall(
    val tool: String,
    val arguments: Map<String, String> = emptyMap(),
)

data class ToolResult(
    val tool: String,
    val ok: Boolean,
    val output: String,
    val error: String? = null,
    val executionRecord: ToolExecutionRecord? = null,
    val durationMillis: Long = 0,
    val estimatedCostMicros: Long = 0,
    val denied: Boolean = false,
) {
    companion object {
        fun success(tool: String, output: String): ToolResult = ToolResult(tool = tool, ok = true, output = output)

        fun failure(tool: String, error: String): ToolResult =
            ToolResult(tool = tool, ok = false, output = "", error = error)
    }
}

data class ToolExecutionContext(
    val runId: RunId? = null,
    val pid: ProcessId? = null,
    val agent: AgentId? = null,
)

data class ToolExecutionRecord(
    val callId: String,
    val runId: RunId?,
    val pid: ProcessId?,
    val agent: AgentId,
    val tool: String,
    val permission: ToolPermission?,
    val allowed: Boolean,
    val denied: Boolean,
    val durationMillis: Long,
    val estimatedCostMicros: Long,
    val redactedArguments: Map<String, String>,
    val error: String? = null,
)

interface SyscallAuditSink {
    fun record(record: ToolExecutionRecord)

    fun records(runId: RunId? = null): List<ToolExecutionRecord>
}

object NoopSyscallAuditSink : SyscallAuditSink {
    override fun record(record: ToolExecutionRecord) = Unit

    override fun records(runId: RunId?): List<ToolExecutionRecord> = emptyList()
}

class InMemorySyscallAuditSink : SyscallAuditSink {
    private val lock = Any()
    private val records = mutableListOf<ToolExecutionRecord>()

    override fun record(record: ToolExecutionRecord) {
        synchronized(lock) {
            records += record
        }
    }

    override fun records(runId: RunId?): List<ToolExecutionRecord> = synchronized(lock) {
        records.filter { runId == null || it.runId == runId }
    }
}

class CostLedger {
    private val lock = Any()
    private val records = mutableListOf<ToolExecutionRecord>()

    fun record(record: ToolExecutionRecord) {
        synchronized(lock) {
            records += record
        }
    }

    fun records(runId: RunId? = null): List<ToolExecutionRecord> = synchronized(lock) {
        records.filter { runId == null || it.runId == runId }
    }

    fun estimatedCostMicros(runId: RunId? = null): Long =
        records(runId).sumOf { it.estimatedCostMicros }
}

interface Tool {
    val name: String
    val description: String
    val permission: ToolPermission

    fun call(call: ToolCall): ToolResult
}

class ToolRegistry(
    tools: Iterable<Tool> = emptyList(),
    private val auditSink: SyscallAuditSink = InMemorySyscallAuditSink(),
    private val costLedger: CostLedger = CostLedger(),
) {
    private val toolsByName: Map<String, Tool> = tools.associateBy { it.name }
    private val callIds = AtomicLong(1)
    private val callCounts = mutableMapOf<String, Int>()

    val names: Set<String>
        get() = toolsByName.keys

    fun get(name: String): Tool? = toolsByName[name]

    fun auditRecords(runId: RunId? = null): List<ToolExecutionRecord> = auditSink.records(runId)

    fun estimatedCostMicros(runId: RunId? = null): Long = costLedger.estimatedCostMicros(runId)

    fun execute(agent: AgentSpec, call: ToolCall, context: ToolExecutionContext = ToolExecutionContext()): ToolResult {
        val started = System.nanoTime()
        val agentId = context.agent ?: agent.id
        val callId = "sys-${callIds.getAndIncrement()}"
        val tool = toolsByName[call.tool]
            ?: return deniedResult(callId, context, agentId, call, null, started, "Tool '${call.tool}' is not registered.")

        val grant = if (agent.capabilities.isNotEmpty()) {
            agent.capabilities.firstOrNull { it.tool == tool.name }
                ?: return deniedResult(callId, context, agentId, call, tool.permission, started, "Agent '${agent.id}' is not allowed to call '${call.tool}'.")
        } else {
            if (tool.name !in agent.allowedTools) {
                return deniedResult(callId, context, agentId, call, tool.permission, started, "Agent '${agent.id}' is not allowed to call '${call.tool}'.")
            }
            if (tool.permission !in agent.permissions) {
                return deniedResult(callId, context, agentId, call, tool.permission, started, "Agent '${agent.id}' lacks permission '${tool.permission}'.")
            }
            ToolCapabilityGrant(tool = tool.name, permission = tool.permission)
        }

        if (grant.permission != tool.permission) {
            return deniedResult(
                callId,
                context,
                agentId,
                call,
                tool.permission,
                started,
                "Agent '${agent.id}' capability for '${call.tool}' does not grant permission '${tool.permission}'.",
            )
        }

        if (!scopeAllows(grant, call)) {
            return deniedResult(callId, context, agentId, call, tool.permission, started, "Tool '${call.tool}' denied by capability scope '${grant.scope}'.")
        }

        val countKey = listOf(context.runId?.value ?: "*", agentId.value, call.tool).joinToString(":")
        val nextCount = (callCounts[countKey] ?: 0) + 1
        val maxCalls = grant.limits.maxCalls
        if (maxCalls != null && nextCount > maxCalls) {
            return deniedResult(callId, context, agentId, call, tool.permission, started, "Tool '${call.tool}' denied by maxCalls=$maxCalls.")
        }

        callCounts[countKey] = nextCount
        val raw = tool.call(call)
        val durationMillis = elapsedMillis(started)
        val maxMillis = grant.limits.maxMillis
        val finalResult = if (maxMillis != null && durationMillis > maxMillis) {
            ToolResult.failure(call.tool, "Tool '${call.tool}' denied by maxMillis=$maxMillis after ${durationMillis}ms.")
        } else {
            raw
        }
        val denied = !finalResult.ok && finalResult.error.orEmpty().contains("denied", ignoreCase = true)
        val estimatedCostMicros = grant.cost.estimatedMicros
        val record = ToolExecutionRecord(
            callId = callId,
            runId = context.runId,
            pid = context.pid,
            agent = agentId,
            tool = call.tool,
            permission = tool.permission,
            allowed = finalResult.ok,
            denied = denied,
            durationMillis = durationMillis,
            estimatedCostMicros = estimatedCostMicros,
            redactedArguments = redactArguments(call.arguments),
            error = finalResult.error,
        )
        auditSink.record(record)
        costLedger.record(record)
        return finalResult.copy(
            executionRecord = record,
            durationMillis = durationMillis,
            estimatedCostMicros = estimatedCostMicros,
            denied = denied,
        )
    }

    private fun deniedResult(
        callId: String,
        context: ToolExecutionContext,
        agent: AgentId,
        call: ToolCall,
        permission: ToolPermission?,
        started: Long,
        error: String,
    ): ToolResult {
        val durationMillis = elapsedMillis(started)
        val record = ToolExecutionRecord(
            callId = callId,
            runId = context.runId,
            pid = context.pid,
            agent = agent,
            tool = call.tool,
            permission = permission,
            allowed = false,
            denied = true,
            durationMillis = durationMillis,
            estimatedCostMicros = 0,
            redactedArguments = redactArguments(call.arguments),
            error = error,
        )
        auditSink.record(record)
        costLedger.record(record)
        return ToolResult.failure(call.tool, error).copy(
            executionRecord = record,
            durationMillis = durationMillis,
            denied = true,
        )
    }

    private fun scopeAllows(grant: ToolCapabilityGrant, call: ToolCall): Boolean {
        if (grant.scope == "*") return true
        val scopedValue = call.arguments["path"] ?: call.arguments["url"] ?: call.arguments["message"] ?: return false
        return scopedValue == grant.scope || scopedValue.startsWith(grant.scope.trimEnd('/') + "/")
    }

    private fun elapsedMillis(started: Long): Long =
        ((System.nanoTime() - started) / 1_000_000).coerceAtLeast(0)

    private fun redactArguments(arguments: Map<String, String>): Map<String, String> =
        arguments.mapValues { (key, value) ->
            if (key.contains("secret", ignoreCase = true) ||
                key.contains("token", ignoreCase = true) ||
                key.contains("password", ignoreCase = true)
            ) {
                "<redacted>"
            } else {
                value.take(160)
            }
        }

    companion object {
        val Empty: ToolRegistry = ToolRegistry()
    }
}
