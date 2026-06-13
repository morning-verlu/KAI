package ai.kaios

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.time.Duration

data class OllamaConfig(
    val model: String,
    val baseUrl: String = "http://localhost:11434",
    val timeout: Duration = Duration.ofSeconds(120),
    val temperature: Double? = null,
) {
    init {
        require(model.isNotBlank()) { "Ollama provider requires a non-blank model." }
        require(baseUrl.isNotBlank()) { "Ollama provider requires a non-blank base URL." }
    }

    companion object {
        fun fromEnv(
            env: (String) -> String? = System::getenv,
        ): OllamaConfig {
            val model = env("OLLAMA_MODEL")
                ?: error("OLLAMA_MODEL is required when KAIOS_MODEL_PROVIDER=ollama.")
            return OllamaConfig(
                model = model,
                baseUrl = env("OLLAMA_BASE_URL") ?: "http://localhost:11434",
                temperature = env("OLLAMA_TEMPERATURE")?.toDoubleOrNull(),
            )
        }
    }
}

class OllamaModelProvider(
    private val config: OllamaConfig,
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
                uri = URI.create("${config.baseUrl.trimEnd('/')}/api/chat"),
                headers = emptyMap(),
                body = json.encodeToString(chatRequestBody(messages)),
                timeout = config.timeout,
            ),
        )

        if (response.statusCode !in 200..299) {
            throw ModelProviderHttpException("Ollama provider returned HTTP ${response.statusCode}: ${response.body.take(500)}")
        }

        val decoded = json.decodeFromString<OllamaChatResponse>(response.body)
        val content = decoded.message?.content?.trim()
            ?: error("Ollama provider returned no message content.")

        return ModelResponse(
            content = content,
            tokenUsage = TokenUsage(
                input = decoded.promptEvalCount ?: estimateProviderTokens(messages.joinToString(" ") { it.content }),
                output = decoded.evalCount ?: estimateProviderTokens(content),
            ),
        )
    }

    private fun chatRequestBody(messages: List<ProviderChatMessage>): JsonObject = buildJsonObject {
        put("model", config.model)
        put("stream", false)
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
        config.temperature?.let { temperature ->
            put(
                "options",
                buildJsonObject {
                    put("temperature", JsonPrimitive(temperature))
                },
            )
        }
    }
}

@Serializable
private data class OllamaChatResponse(
    val message: OllamaMessage? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Int? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null,
)

@Serializable
private data class OllamaMessage(
    val content: String? = null,
)
