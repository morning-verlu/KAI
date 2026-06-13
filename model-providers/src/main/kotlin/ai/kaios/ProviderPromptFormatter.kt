package ai.kaios

internal data class ProviderChatMessage(
    val role: String,
    val content: String,
)

internal object ProviderPromptFormatter {
    fun messages(request: ModelRequest): List<ProviderChatMessage> {
        val messages = mutableListOf<ProviderChatMessage>()
        messages += ProviderChatMessage("system", systemPrompt(request))

        request.memory.forEach { entry ->
            val role = when (entry.role.lowercase()) {
                "assistant" -> "assistant"
                "system" -> "system"
                else -> "user"
            }
            messages += ProviderChatMessage(role, entry.content)
        }

        if (request.dependencyContext.isNotEmpty()) {
            messages += ProviderChatMessage(
                "user",
                buildString {
                    appendLine("Dependency outputs:")
                    request.dependencyContext.forEach { (node, output) ->
                        appendLine("[$node]")
                        appendLine(output)
                    }
                }.trim(),
            )
        }

        messages += ProviderChatMessage("user", request.input)
        return messages
    }

    private fun systemPrompt(request: ModelRequest): String = buildString {
        append("You are KAI OS agent '${request.agent.id.value}', running as an isolated agent process.")
        if (request.agent.instruction.isNotBlank()) {
            appendLine()
            append(request.agent.instruction)
        }
        if (request.availableTools.isNotEmpty()) {
            appendLine()
            append("Available syscalls: ${request.availableTools.sorted().joinToString(", ")}.")
        }
        appendLine()
        append("Return concise output for this process.")
    }
}

internal fun estimateProviderTokens(text: String): Int =
    text.trim()
        .split(Regex("\\s+"))
        .count { it.isNotBlank() }
        .coerceAtLeast(1)
