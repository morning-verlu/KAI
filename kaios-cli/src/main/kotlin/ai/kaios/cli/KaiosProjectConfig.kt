package ai.kaios.cli

import ai.kaios.MemoryStore
import ai.kaios.MemoryIsolation
import ai.kaios.ProcessRecoveryPolicy
import ai.kaios.RuntimeEventType
import ai.kaios.ToolCapabilityLimits
import ai.kaios.ToolCostProfile
import ai.kaios.ToolPermission
import ai.kaios.ToolRegistry
import ai.kaios.agent
import ai.kaios.workflow
import ai.kaios.Workflow
import ai.kaios.AgentId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal const val KAIOS_CONFIG_FILE = "kaios.json"

@Serializable
internal data class KaiosProjectConfig(
    val name: String = "default",
    val agents: List<KaiosAgentConfig> = emptyList(),
)

@Serializable
internal data class KaiosAgentConfig(
    val id: String,
    val instruction: String = "",
    val tools: List<String> = emptyList(),
    val dependsOn: List<String> = emptyList(),
    val fallback: String? = null,
    val fallbackOnly: Boolean = false,
    val retries: Int = 0,
    val memory: Boolean = true,
    val memoryIsolation: String = "AGENT",
    val priority: Int = 0,
    val recovery: KaiosRecoveryConfig? = null,
    val triggers: List<KaiosTriggerConfig> = emptyList(),
    val executorHint: String? = null,
    val capabilities: List<KaiosCapabilityConfig> = emptyList(),
)

@Serializable
internal data class KaiosRecoveryConfig(
    val maxRestarts: Int = 0,
    val backoffMillis: Long = 0,
    val memoryIsolation: String = "AGENT",
)

@Serializable
internal data class KaiosTriggerConfig(
    val eventType: String,
    val agent: String? = null,
    val node: String? = null,
)

@Serializable
internal data class KaiosCapabilityConfig(
    val tool: String,
    val permission: String,
    val scope: String = "*",
    val maxCalls: Int? = null,
    val maxMillis: Long? = null,
    val estimatedCostMicros: Long = 0,
)

internal data class KaiosProjectTemplate(
    val id: String,
    val description: String,
    val exampleTask: String,
    val config: KaiosProjectConfig,
)

internal val kaiosConfigJson: Json = Json {
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = false
}

internal val projectConfigTemplates: List<KaiosProjectTemplate> = listOf(
    KaiosProjectTemplate(
        id = "default",
        description = "Planner -> executor -> validator baseline workflow.",
        exampleTask = "analyze crypto market",
        config = KaiosProjectConfig(
            name = "default",
            agents = listOf(
                KaiosAgentConfig(
                    id = "planner",
                    instruction = "Plan the task as an agent process.",
                    tools = listOf("echo", "clock"),
                ),
                KaiosAgentConfig(
                    id = "executor",
                    instruction = "Execute the plan through permitted syscalls.",
                    tools = listOf("echo", "mock-http"),
                    dependsOn = listOf("planner"),
                ),
                KaiosAgentConfig(
                    id = "validator",
                    instruction = "Validate the executor output.",
                    tools = listOf("echo"),
                    dependsOn = listOf("executor"),
                ),
            ),
        ),
    ),
    KaiosProjectTemplate(
        id = "research",
        description = "Research, synthesize, and validate an answer.",
        exampleTask = "map the JVM agent runtime",
        config = KaiosProjectConfig(
            name = "research",
            agents = listOf(
                KaiosAgentConfig(
                    id = "researcher",
                    instruction = "Gather facts, constraints, and useful context for the task.",
                    tools = listOf("echo", "clock", "mock-http"),
                ),
                KaiosAgentConfig(
                    id = "synthesizer",
                    instruction = "Turn the research context into a concise, useful answer.",
                    tools = listOf("echo"),
                    dependsOn = listOf("researcher"),
                ),
                KaiosAgentConfig(
                    id = "validator",
                    instruction = "Check the answer for gaps, contradictions, and missing next steps.",
                    tools = listOf("echo"),
                    dependsOn = listOf("synthesizer"),
                ),
            ),
        ),
    ),
    KaiosProjectTemplate(
        id = "code-review",
        description = "Inspect, reason about, and validate a code change.",
        exampleTask = "review the latest code change",
        config = KaiosProjectConfig(
            name = "code-review",
            agents = listOf(
                KaiosAgentConfig(
                    id = "inspector",
                    instruction = "Inspect the requested code or design change and identify the important surfaces.",
                    tools = listOf("echo", "file"),
                ),
                KaiosAgentConfig(
                    id = "reviewer",
                    instruction = "Prioritize concrete bugs, regressions, missing tests, and risky assumptions.",
                    tools = listOf("echo", "file"),
                    dependsOn = listOf("inspector"),
                ),
                KaiosAgentConfig(
                    id = "validator",
                    instruction = "Validate whether the review findings are actionable and well supported.",
                    tools = listOf("echo"),
                    dependsOn = listOf("reviewer"),
                ),
            ),
        ),
    ),
    KaiosProjectTemplate(
        id = "release",
        description = "Plan, execute, verify, and summarize a release.",
        exampleTask = "prepare v0.2.0",
        config = KaiosProjectConfig(
            name = "release",
            agents = listOf(
                KaiosAgentConfig(
                    id = "planner",
                    instruction = "Plan the release steps, risks, and verification commands.",
                    tools = listOf("echo", "clock"),
                ),
                KaiosAgentConfig(
                    id = "executor",
                    instruction = "Execute the release plan through safe, observable steps.",
                    tools = listOf("echo", "mock-http", "file"),
                    dependsOn = listOf("planner"),
                ),
                KaiosAgentConfig(
                    id = "verifier",
                    instruction = "Verify release artifacts, docs, and installation paths.",
                    tools = listOf("echo", "file"),
                    dependsOn = listOf("executor"),
                ),
                KaiosAgentConfig(
                    id = "announcer",
                    instruction = "Prepare a concise release summary with install and verification notes.",
                    tools = listOf("echo"),
                    dependsOn = listOf("verifier"),
                ),
            ),
        ),
    ),
)

internal fun requireProjectTemplate(id: String): KaiosProjectTemplate {
    val normalized = id.lowercase().trim()
    return projectConfigTemplates.firstOrNull { it.id == normalized }
        ?: error("Unknown template '$id'. Use one of: ${projectConfigTemplates.joinToString(", ") { it.id }}.")
}

internal fun projectConfigText(templateId: String = "default"): String =
    kaiosConfigJson.encodeToString(requireProjectTemplate(templateId).config) + "\n"

internal fun loadProjectWorkflow(path: Path, memory: MemoryStore, tools: ToolRegistry): Workflow {
    val config = loadProjectConfig(path)
    return config.toWorkflow(memory, tools.names)
}

internal fun loadProjectConfig(path: Path): KaiosProjectConfig {
    require(path.exists()) { "Config file '$path' was not found." }
    return runCatching {
        kaiosConfigJson.decodeFromString<KaiosProjectConfig>(path.readText())
    }.getOrElse { failure ->
        error("Invalid KAI OS config '$path': ${failure.message}")
    }
}

internal fun KaiosProjectConfig.toWorkflow(memory: MemoryStore, knownTools: Set<String>): Workflow {
    validate(knownTools)

    val specs = agents.associate { configuredAgent ->
        configuredAgent.id to agent(configuredAgent.id) {
            instruction(configuredAgent.instruction)
            if (configuredAgent.capabilities.isEmpty()) {
                configuredAgent.tools.forEach { tool(it) }
            } else {
                configuredAgent.capabilities.forEach { grant ->
                    capability(
                        tool = grant.tool,
                        permission = parseToolPermission(grant.permission),
                        scope = grant.scope,
                        limits = ToolCapabilityLimits(maxCalls = grant.maxCalls, maxMillis = grant.maxMillis),
                        cost = ToolCostProfile(estimatedMicros = grant.estimatedCostMicros),
                    )
                }
            }
            if (configuredAgent.memory) this.memory(memory)
            memoryIsolation(parseMemoryIsolation(configuredAgent.memoryIsolation))
        }
    }

    return workflow(name) {
        agents.forEach { configuredAgent ->
            node(configuredAgent.id, specs.getValue(configuredAgent.id)).apply {
                if (configuredAgent.dependsOn.isNotEmpty()) {
                    dependsOn(*configuredAgent.dependsOn.toTypedArray())
                }
                configuredAgent.fallback?.let { fallbackTo(it) }
                if (configuredAgent.fallbackOnly) fallbackOnly()
                retries(configuredAgent.retries)
                priority(configuredAgent.priority)
                configuredAgent.recovery?.let { recovery ->
                    recovery(
                        ProcessRecoveryPolicy(
                            maxRestarts = recovery.maxRestarts,
                            backoff = Duration.ofMillis(recovery.backoffMillis),
                            memoryIsolation = parseMemoryIsolation(recovery.memoryIsolation),
                        ),
                    )
                }
                configuredAgent.triggers.forEach { trigger ->
                    triggeredBy(
                        eventType = parseRuntimeEventType(trigger.eventType),
                        agent = trigger.agent?.let(::AgentId),
                        nodeId = trigger.node,
                    )
                }
                configuredAgent.executorHint?.let { executorHint(it) }
            }
        }
    }
}

private fun KaiosProjectConfig.validate(knownTools: Set<String>) {
    require(name.isNotBlank()) { "Config field 'name' cannot be blank." }
    require(agents.isNotEmpty()) { "Config field 'agents' must contain at least one agent." }
    require(agents.any { !it.fallbackOnly }) { "Config must include at least one non-fallback agent." }

    val ids = agents.map { it.id }
    require(ids.all { it.isNotBlank() }) { "Agent id cannot be blank." }

    val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    require(duplicates.isEmpty()) { "Agent ids must be unique: ${duplicates.sorted().joinToString(", ")}." }

    val knownIds = ids.toSet()
    agents.forEach { configuredAgent ->
        require(configuredAgent.retries in 0..10) {
            "Agent '${configuredAgent.id}' retries must be between 0 and 10."
        }
        parseMemoryIsolation(configuredAgent.memoryIsolation)
        configuredAgent.executorHint?.let {
            require(it.isNotBlank()) { "Agent '${configuredAgent.id}' executorHint cannot be blank." }
        }
        configuredAgent.recovery?.let { recovery ->
            require(recovery.maxRestarts in 0..10) { "Agent '${configuredAgent.id}' recovery.maxRestarts must be between 0 and 10." }
            require(recovery.backoffMillis >= 0) { "Agent '${configuredAgent.id}' recovery.backoffMillis cannot be negative." }
            parseMemoryIsolation(recovery.memoryIsolation)
        }

        val unknownTools = configuredAgent.tools.filterNot { it in knownTools }.toSortedSet()
        require(unknownTools.isEmpty()) {
            "Agent '${configuredAgent.id}' references unknown tool(s): ${unknownTools.joinToString(", ")}."
        }
        val unknownCapabilityTools = configuredAgent.capabilities.map { it.tool }.filterNot { it in knownTools }.toSortedSet()
        require(unknownCapabilityTools.isEmpty()) {
            "Agent '${configuredAgent.id}' references unknown capability tool(s): ${unknownCapabilityTools.joinToString(", ")}."
        }
        configuredAgent.capabilities.forEach { capability ->
            parseToolPermission(capability.permission)
            require(capability.scope.isNotBlank()) { "Agent '${configuredAgent.id}' capability '${capability.tool}' scope cannot be blank." }
            require(capability.maxCalls == null || capability.maxCalls >= 0) { "Agent '${configuredAgent.id}' capability '${capability.tool}' maxCalls cannot be negative." }
            require(capability.maxMillis == null || capability.maxMillis >= 0) { "Agent '${configuredAgent.id}' capability '${capability.tool}' maxMillis cannot be negative." }
            require(capability.estimatedCostMicros >= 0) { "Agent '${configuredAgent.id}' capability '${capability.tool}' estimatedCostMicros cannot be negative." }
        }

        val unknownDependencies = configuredAgent.dependsOn.filterNot { it in knownIds }.toSortedSet()
        require(unknownDependencies.isEmpty()) {
            "Agent '${configuredAgent.id}' depends on unknown agent(s): ${unknownDependencies.joinToString(", ")}."
        }

        configuredAgent.fallback?.let { fallback ->
            require(fallback in knownIds) { "Agent '${configuredAgent.id}' references unknown fallback agent '$fallback'." }
            require(fallback != configuredAgent.id) { "Agent '${configuredAgent.id}' cannot fallback to itself." }
        }
        configuredAgent.triggers.forEach { trigger ->
            parseRuntimeEventType(trigger.eventType)
            trigger.agent?.let { agentId ->
                require(agentId in knownIds) { "Agent '${configuredAgent.id}' trigger references unknown agent '$agentId'." }
            }
            trigger.node?.let { nodeId ->
                require(nodeId in knownIds) { "Agent '${configuredAgent.id}' trigger references unknown node '$nodeId'." }
            }
        }
    }

    dependencyCycle(ids, agents.associate { it.id to it.dependsOn })?.let { cycle ->
        error("Workflow dependencies contain a cycle: ${cycle.joinToString(" -> ")}.")
    }
}

private fun parseMemoryIsolation(value: String): MemoryIsolation =
    runCatching { MemoryIsolation.valueOf(value.uppercase()) }
        .getOrElse { error("Unknown memory isolation '$value'. Use AGENT, PROCESS, or WORKFLOW.") }

private fun parseToolPermission(value: String): ToolPermission =
    runCatching { ToolPermission.valueOf(value.uppercase()) }
        .getOrElse { error("Unknown tool permission '$value'.") }

private fun parseRuntimeEventType(value: String): RuntimeEventType =
    runCatching { RuntimeEventType.valueOf(value.uppercase()) }
        .getOrElse { error("Unknown runtime event type '$value'.") }

private fun dependencyCycle(ids: List<String>, dependencies: Map<String, List<String>>): List<String>? {
    val visiting = linkedSetOf<String>()
    val visited = linkedSetOf<String>()

    fun visit(id: String): List<String>? {
        if (id in visited) return null
        if (id in visiting) {
            val cycleStart = visiting.indexOf(id)
            return visiting.drop(cycleStart) + id
        }

        visiting += id
        dependencies.getValue(id).forEach { dependency ->
            visit(dependency)?.let { return it }
        }
        visiting -= id
        visited += id
        return null
    }

    ids.forEach { id ->
        visit(id)?.let { return it }
    }
    return null
}
