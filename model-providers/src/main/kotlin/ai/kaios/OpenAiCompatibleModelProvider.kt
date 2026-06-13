package ai.kaios

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.time.Duration

data class OpenAiCompatibleConfig(
    val apiKey: String,
    val model: String,
    val baseUrl: String = "https://api.openai.com/v1",
    val timeout: Duration = Duration.ofSeconds(60),
    val temperature: Double? = null,
) {
    init {
        require(apiKey.isNotBlank()) { "OpenAI-compatible provider requires a non-blank API key." }
        require(model.isNotBlank()) { "OpenAI-compatible provider requires a non-blank model." }
        require(baseUrl.isNotBlank()) { "OpenAI-compatible provider requires a non-blank base URL." }
    }

    companion object {
        fun fromEnv(
            env: (String) -> String? = System::getenv,
        ): OpenAiCompatibleConfig {
            val apiKey = env("OPENAI_API_KEY")
                ?: error("OPENAI_API_KEY is required when KAIOS_MODEL_PROVIDER=openai.")
            val model = env("OPENAI_MODEL")
                ?: error("OPENAI_MODEL is required when KAIOS_MODEL_PROVIDER=openai.")
            return OpenAiCompatibleConfig(
                apiKey = apiKey,
                model = model,
                baseUrl = env("OPENAI_BASE_URL") ?: "https://api.openai.com/v1",
                temperature = env("OPENAI_TEMPERATURE")?.toDoubleOrNull(),
            )
        }
    }
}

class OpenAiCompatibleModelProvider(
    private val config: OpenAiCompatibleConfig,
    private val httpClient: ProviderHttpClient = JdkProviderHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    },
) : ModelProvider {
    override fun complete(request: ModelRequest): ModelResponse {
        val messages = ProviderPromptFormatter.messages(request)
        val response = httpClient.postJson(
            ProviderHttpRequest(
                uri = URI.create("${config.baseUrl.trimEnd('/')}/chat/completions"),
                headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
                body = json.encodeToString(chatRequestBody(messages)),
                timeout = config.timeout,
            ),
        )

        if (response.statusCode !in 200..299) {
            throw ModelProviderHttpException(
                "OpenAI-compatible provider returned HTTP ${response.statusCode}: ${response.body.take(500)}",
            )
        }

        val decoded = json.decodeFromString<OpenAiChatCompletionResponse>(response.body)
        val content = decoded.choices.firstOrNull()?.message?.content?.trim()
            ?: error("OpenAI-compatible provider returned no message content.")

        return ModelResponse(
            content = content,
            tokenUsage = TokenUsage(
                input = decoded.usage?.promptTokens
                    ?: estimateProviderTokens(messages.joinToString(" ") { it.content }),
                output = decoded.usage?.completionTokens ?: estimateProviderTokens(content),
            ),
        )
    }

    private fun chatRequestBody(messages: List<ProviderChatMessage>): JsonObject = buildJsonObject {
        put("model", config.model)
        put(
            "messages",
            JsonArray(
                messages.map { message ->
                    buildJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    }
                },
            ),
        )
        config.temperature?.let { put("temperature", JsonPrimitive(it)) }
    }
}

@Serializable
private data class OpenAiChatCompletionResponse(
    val choices: List<OpenAiChoice> = emptyList(),
    val usage: OpenAiUsage? = null,
)

@Serializable
private data class OpenAiChoice(
    val message: OpenAiMessage? = null,
)

@Serializable
private data class OpenAiMessage(
    val content: String? = null,
)

@Serializable
private data class OpenAiUsage(
    @SerialName("prompt_tokens")
    val promptTokens: Int? = null,
    @SerialName("completion_tokens")
    val completionTokens: Int? = null,
)
