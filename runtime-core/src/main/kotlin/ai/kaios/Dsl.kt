package ai.kaios

fun agent(id: String, block: AgentBuilder.() -> Unit = {}): AgentSpec =
    AgentBuilder(id).apply(block).build()

class AgentBuilder(
    private val id: String,
) {
    private val tools = linkedSetOf<String>()
    private val permissions = linkedSetOf<ToolPermission>()
    private val capabilities = linkedSetOf<ToolCapabilityGrant>()
    private var instruction: String = ""
    private var memoryEnabled: Boolean = false
    private var memoryIsolation: MemoryIsolation = MemoryIsolation.AGENT

    fun instruction(value: String) {
        instruction = value
    }

    fun tool(name: String) {
        tools += name
        when (name) {
            "echo" -> permissions += ToolPermission.ECHO
            "clock" -> permissions += ToolPermission.READ_CLOCK
            "mock-http" -> permissions += ToolPermission.NETWORK
            "http" -> permissions += ToolPermission.NETWORK
            "file" -> permissions += ToolPermission.FILE
        }
    }

    fun permission(permission: ToolPermission) {
        permissions += permission
    }

    fun capability(
        tool: String,
        permission: ToolPermission,
        scope: String = "*",
        limits: ToolCapabilityLimits = ToolCapabilityLimits(),
        cost: ToolCostProfile = ToolCostProfile(),
    ) {
        tools += tool
        permissions += permission
        capabilities += ToolCapabilityGrant(tool, permission, scope, limits, cost)
    }

    fun memory(store: MemoryStore) {
        memoryEnabled = store !is NoopMemoryStore
    }

    fun memoryIsolation(isolation: MemoryIsolation) {
        memoryIsolation = isolation
    }

    fun build(): AgentSpec = AgentSpec(
        id = AgentId(id),
        instruction = instruction,
        allowedTools = tools.toSet(),
        permissions = permissions.toSet(),
        capabilities = capabilities.toSet(),
        memoryEnabled = memoryEnabled,
        memoryIsolation = memoryIsolation,
    )
}

fun workflow(name: String, block: WorkflowBuilder.() -> Unit): Workflow =
    WorkflowBuilder(name).apply(block).build()

class WorkflowBuilder(
    private val name: String,
) {
    private val nodes = linkedMapOf<String, WorkflowNodeBuilder>()

    fun node(id: String, agent: AgentSpec = agent(id)): WorkflowNodeBuilder {
        val builder = WorkflowNodeBuilder(id, agent)
        nodes[id] = builder
        return builder
    }

    fun build(): Workflow = Workflow(name, nodes.values.map { it.build() })
}

class WorkflowNodeBuilder(
    private val id: String,
    private val agent: AgentSpec,
) {
    private val dependencies = linkedSetOf<String>()
    private var fallback: String? = null
    private var fallbackOnly: Boolean = false
    private var maxAttempts: Int = 1
    private var priority: Int = 0
    private var recoveryPolicy: ProcessRecoveryPolicy = ProcessRecoveryPolicy()
    private val triggers = mutableListOf<WorkflowTrigger>()
    private var executorHint: String? = null

    fun dependsOn(vararg ids: String): WorkflowNodeBuilder = apply {
        dependencies += ids
    }

    fun fallbackTo(id: String): WorkflowNodeBuilder = apply {
        fallback = id
    }

    fun fallbackOnly(): WorkflowNodeBuilder = apply {
        fallbackOnly = true
    }

    fun retries(count: Int): WorkflowNodeBuilder = apply {
        require(count >= 0) { "Retry count cannot be negative." }
        maxAttempts = count + 1
    }

    fun priority(value: Int): WorkflowNodeBuilder = apply {
        priority = value
    }

    fun recovery(policy: ProcessRecoveryPolicy): WorkflowNodeBuilder = apply {
        recoveryPolicy = policy
    }

    fun recovery(maxRestarts: Int, memoryIsolation: MemoryIsolation = MemoryIsolation.AGENT): WorkflowNodeBuilder = apply {
        recoveryPolicy = ProcessRecoveryPolicy(maxRestarts = maxRestarts, memoryIsolation = memoryIsolation)
    }

    fun triggeredBy(eventType: RuntimeEventType, agent: AgentId? = null, nodeId: String? = null): WorkflowNodeBuilder = apply {
        triggers += WorkflowTrigger(eventType = eventType, agent = agent, nodeId = nodeId)
    }

    fun executorHint(value: String): WorkflowNodeBuilder = apply {
        executorHint = value
    }

    fun build(): WorkflowNode = WorkflowNode(
        id = id,
        agent = agent,
        dependencies = dependencies.toSet(),
        fallback = fallback,
        fallbackOnly = fallbackOnly,
        maxAttempts = maxAttempts,
        priority = priority,
        recoveryPolicy = recoveryPolicy,
        triggers = triggers.toList(),
        executorHint = executorHint,
    )
}
