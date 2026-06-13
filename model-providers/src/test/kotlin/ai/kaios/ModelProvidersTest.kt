package ai.kaios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelProvidersTest {
    @Test
    fun `openai compatible provider posts chat completion request and parses usage`() {
        val transport = RecordingTransport(
            ProviderHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "choices": [
                        { "message": { "content": "real provider output" } }
                      ],
                      "usage": {
                        "prompt_tokens": 11,
                        "completion_tokens": 7
                      }
                    }
                """.trimIndent(),
            ),
        )
        val provider = OpenAiCompatibleModelProvider(
            config = OpenAiCompatibleConfig(
                apiKey = "test-key",
                model = "test-model",
                baseUrl = "https://llm.example/v1",
            ),
            httpClient = transport,
        )

        val response = provider.complete(modelRequest())

        assertEquals("real provider output", response.content)
        assertEquals(TokenUsage(input = 11, output = 7), response.tokenUsage)
        assertEquals("https://llm.example/v1/chat/completions", transport.lastRequest?.uri.toString())
        assertEquals("Bearer test-key", transport.lastRequest?.headers?.get("Authorization"))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"model\":\"test-model\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"role\":\"system\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("Dependency outputs"))
    }

    @Test
    fun `ollama provider posts chat request and parses eval counts`() {
        val transport = RecordingTransport(
            ProviderHttpResponse(
                statusCode = 200,
                body = """
                    {
                      "message": { "content": "local model output" },
                      "prompt_eval_count": 9,
                      "eval_count": 5
                    }
                """.trimIndent(),
            ),
        )
        val provider = OllamaModelProvider(
            config = OllamaConfig(
                model = "local-model",
                baseUrl = "http://localhost:11434",
            ),
            httpClient = transport,
        )

        val response = provider.complete(modelRequest())

        assertEquals("local model output", response.content)
        assertEquals(TokenUsage(input = 9, output = 5), response.tokenUsage)
        assertEquals("http://localhost:11434/api/chat", transport.lastRequest?.uri.toString())
        assertTrue(transport.lastRequest?.headers.orEmpty().isEmpty())
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"model\":\"local-model\""))
        assertTrue(transport.lastRequest?.body.orEmpty().contains("\"stream\":false"))
    }

    @Test
    fun `provider http errors surface status without exposing request headers`() {
        val transport = RecordingTransport(
            ProviderHttpResponse(
                statusCode = 401,
                body = """{"error":"bad key"}""",
            ),
        )
        val provider = OpenAiCompatibleModelProvider(
            config = OpenAiCompatibleConfig(
                apiKey = "secret-key",
                model = "test-model",
                baseUrl = "https://llm.example/v1",
            ),
            httpClient = transport,
        )

        val error = kotlin.runCatching { provider.complete(modelRequest()) }.exceptionOrNull()

        assertTrue(error is ModelProviderHttpException)
        assertTrue(error.message.orEmpty().contains("HTTP 401"))
        assertTrue(!error.message.orEmpty().contains("secret-key"))
    }

    private fun modelRequest(): ModelRequest = ModelRequest(
        runId = RunId("run-provider"),
        agent = AgentSpec(
            id = AgentId("planner"),
            instruction = "Plan carefully.",
            allowedTools = setOf("echo"),
        ),
        input = "ship a provider",
        dependencyContext = mapOf("input" to "user request"),
        memory = listOf(
            MemoryEntry(
                runId = RunId("run-provider"),
                agent = AgentId("planner"),
                role = "assistant",
                content = "previous output",
                timestamp = java.time.Instant.parse("2026-06-13T12:00:00Z"),
            ),
        ),
        availableTools = setOf("echo"),
    )

    private class RecordingTransport(
        private val response: ProviderHttpResponse,
    ) : ProviderHttpClient {
        var lastRequest: ProviderHttpRequest? = null

        override fun postJson(request: ProviderHttpRequest): ProviderHttpResponse {
            lastRequest = request
            return response
        }
    }
}
