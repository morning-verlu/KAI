package ai.kaios

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class ProviderHttpRequest(
    val uri: URI,
    val headers: Map<String, String>,
    val body: String,
    val timeout: Duration,
)

data class ProviderHttpResponse(
    val statusCode: Int,
    val body: String,
)

fun interface ProviderHttpClient {
    fun postJson(request: ProviderHttpRequest): ProviderHttpResponse
}

class JdkProviderHttpClient(
    private val client: HttpClient = HttpClient.newBuilder().build(),
) : ProviderHttpClient {
    override fun postJson(request: ProviderHttpRequest): ProviderHttpResponse {
        val builder = HttpRequest.newBuilder(request.uri)
            .timeout(request.timeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(request.body))

        request.headers.forEach { (name, value) -> builder.header(name, value) }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        return ProviderHttpResponse(response.statusCode(), response.body())
    }
}

class ModelProviderHttpException(
    message: String,
) : RuntimeException(message)
