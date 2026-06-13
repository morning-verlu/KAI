package ai.kaios.cli

import ai.kaios.FileRunSnapshotStore
import ai.kaios.MockModelProvider
import ai.kaios.OllamaModelProvider
import ai.kaios.OpenAiCompatibleModelProvider
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KaiosCliSmokeTest {
    @Test
    fun `run ps and inspect work against a saved mock run`() {
        val root = Files.createTempDirectory("kaios-cli-runs")
        val cli = KaiosCli(FileRunSnapshotStore(root))

        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "analyze", "crypto", "market"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)

        assertEquals(0, runCode)
        assertTrue(runText.contains("validate:"))
        assertTrue(runId != null)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(
            arrayOf("ps", runId),
            PrintStream(psOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val psText = psOut.toString()

        assertEquals(0, psCode)
        assertTrue(psText.contains("planner"))
        assertTrue(psText.contains("executor"))
        assertTrue(psText.contains("validator"))
        assertTrue(psText.contains("SYSCALLS"))

        val inspectOut = ByteArrayOutputStream()
        val inspectCode = cli.run(
            arrayOf("inspect", runId),
            PrintStream(inspectOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val inspectText = inspectOut.toString()

        assertEquals(0, inspectCode)
        assertTrue(inspectText.contains("events:"))
        assertTrue(inspectText.contains("SPAWNED"))
        assertTrue(inspectText.contains("SUCCEEDED"))
    }

    @Test
    fun `model provider env selection supports mock openai and ollama`() {
        assertTrue(modelProviderFromEnv { null } is MockModelProvider)

        val openAi = modelProviderFromEnv { key ->
            mapOf(
                "KAIOS_MODEL_PROVIDER" to "openai",
                "OPENAI_API_KEY" to "test-key",
                "OPENAI_MODEL" to "test-model",
                "OPENAI_BASE_URL" to "https://llm.example/v1",
            )[key]
        }
        assertTrue(openAi is OpenAiCompatibleModelProvider)

        val ollama = modelProviderFromEnv { key ->
            mapOf(
                "KAIOS_MODEL_PROVIDER" to "ollama",
                "OLLAMA_MODEL" to "local-model",
            )[key]
        }
        assertTrue(ollama is OllamaModelProvider)
    }

    @Test
    fun `model provider env selection rejects unsupported providers`() {
        assertFailsWith<IllegalStateException> {
            modelProviderFromEnv { key -> if (key == "KAIOS_MODEL_PROVIDER") "unknown" else null }
        }
    }
}
