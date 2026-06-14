package ai.kaios

data class ModelRequest(
    val runId: RunId,
    val agent: AgentSpec,
    val input: String,
    val dependencyContext: Map<String, String> = emptyMap(),
    val memory: List<MemoryEntry> = emptyList(),
    val availableTools: Set<String> = emptySet(),
)

data class ModelResponse(
    val content: String,
    val tokenUsage: TokenUsage,
    val toolCalls: List<ToolCall> = emptyList(),
)

interface ModelProvider {
    fun complete(request: ModelRequest): ModelResponse
}

class MockModelProvider : ModelProvider {
    override fun complete(request: ModelRequest): ModelResponse {
        val rawInput = request.input.trim()
        val task = rawInput.substringBefore("\n\n[KAIOS_CONTEXT]").trim().ifBlank { "empty task" }
        val agentName = request.agent.id.value.lowercase()
        val fingerprint = stableFingerprint("${request.agent.id.value}:$rawInput:${request.dependencyContext.values.joinToString("|")}")
        val dependencySummary = if (request.dependencyContext.isEmpty()) {
            "no dependencies"
        } else {
            request.dependencyContext.keys.joinToString(prefix = "after ", separator = ", ")
        }

        val content = when {
            "planner" in agentName -> "plan:$fingerprint inspect task, execute with tools, validate output"
            "researcher" in agentName -> "research:$fingerprint gathered context for '$task' $dependencySummary"
            "synthesizer" in agentName -> "synthesize:$fingerprint distilled answer for '$task' $dependencySummary"
            "inspector" in agentName -> "inspect:$fingerprint mapped code surfaces for '$task' $dependencySummary"
            "reviewer" in agentName -> "review:$fingerprint prioritized risks for '$task' $dependencySummary"
            "executor" in agentName -> "execute:$fingerprint synthesized result for '$task' $dependencySummary"
            "verifier" in agentName -> "verify:$fingerprint checked release evidence from $dependencySummary"
            "announcer" in agentName -> "announce:$fingerprint prepared release notes from $dependencySummary"
            "validator" in agentName -> "validate:$fingerprint accepted result from $dependencySummary"
            else -> "agent:$fingerprint processed '$task'"
        }

        val toolCalls = when {
            "planner" in agentName && "echo" in request.availableTools -> listOf(
                ToolCall("echo", mapOf("message" to "planning:$fingerprint")),
            )
            "executor" in agentName && "mock-http" in request.availableTools -> listOf(
                ToolCall("mock-http", mapOf("method" to "GET", "url" to "mock://kaios/tasks/$fingerprint")),
            )
            "researcher" in agentName && "mock-http" in request.availableTools -> listOf(
                ToolCall("mock-http", mapOf("method" to "GET", "url" to "mock://kaios/research/$fingerprint")),
            )
            ("inspector" in agentName || "verifier" in agentName) && "file" in request.availableTools -> listOf(
                ToolCall("file", mapOf("op" to "exists", "path" to ".")),
            )
            "reviewer" in agentName && "echo" in request.availableTools -> listOf(
                ToolCall("echo", mapOf("message" to "reviewed:$fingerprint")),
            )
            "announcer" in agentName && "echo" in request.availableTools -> listOf(
                ToolCall("echo", mapOf("message" to "announced:$fingerprint")),
            )
            "validator" in agentName && "echo" in request.availableTools -> listOf(
                ToolCall("echo", mapOf("message" to "validated:$fingerprint")),
            )
            else -> emptyList()
        }

        return ModelResponse(
            content = content,
            tokenUsage = TokenUsage(
                input = estimateTokens(rawInput + request.dependencyContext.values.joinToString(" ") + request.memory.joinToString(" ") { it.content }),
                output = estimateTokens(content + toolCalls.joinToString(" ") { it.tool }),
            ),
            toolCalls = toolCalls,
        )
    }

    private fun stableFingerprint(value: String): String {
        val hash = value.fold(7) { acc, char -> (acc * 31 + char.code) and 0x7fffffff }
        return hash.toString(16).padStart(8, '0').takeLast(8)
    }

    private fun estimateTokens(text: String): Int =
        text.trim()
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }
            .coerceAtLeast(1)
}
