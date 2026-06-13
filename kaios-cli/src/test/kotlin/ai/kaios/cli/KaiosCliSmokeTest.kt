package ai.kaios.cli

import ai.kaios.FileRunSnapshotStore
import ai.kaios.MockModelProvider
import ai.kaios.OllamaModelProvider
import ai.kaios.OpenAiCompatibleModelProvider
import ai.kaios.SQLiteMemoryStore
import ai.kaios.SessionMemoryStore
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
        val reportRoot = Files.createTempDirectory("kaios-cli-reports")
        val cli = KaiosCli(FileRunSnapshotStore(root), reportRoot)

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

        val runsOut = ByteArrayOutputStream()
        val runsCode = cli.run(
            arrayOf("runs"),
            PrintStream(runsOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runsText = runsOut.toString()

        assertEquals(0, runsCode)
        assertTrue(runsText.contains(runId))
        assertTrue(runsText.contains("success"))

        val reportOut = ByteArrayOutputStream()
        val reportCode = cli.run(
            arrayOf("report", runId),
            PrintStream(reportOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val reportPath = reportRoot.resolve("$runId.html")
        val reportText = Files.readString(reportPath)

        assertEquals(0, reportCode)
        assertTrue(reportOut.toString().contains(reportPath.toAbsolutePath().normalize().toString()))
        assertTrue(reportText.contains("Agent Process Manager"))
        assertTrue(reportText.contains("Process Table"))
        assertTrue(reportText.contains("Workflow Graph"))
        assertTrue(reportText.contains("Lifecycle Events"))
        assertTrue(reportText.contains("planner"))
        assertTrue(reportText.contains("validator"))
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

    @Test
    fun `memory store env selection supports session and sqlite`() {
        assertTrue(memoryStoreFromEnv { null } is SessionMemoryStore)

        val database = Files.createTempDirectory("kaios-cli-sqlite").resolve("memory.db")
        val sqlite = memoryStoreFromEnv { key ->
            mapOf(
                "KAIOS_MEMORY_STORE" to "sqlite",
                "KAIOS_SQLITE_PATH" to database.toString(),
            )[key]
        }

        assertTrue(sqlite is SQLiteMemoryStore)
        assertTrue(Files.exists(database))
    }

    @Test
    fun `memory store env selection rejects unsupported stores`() {
        assertFailsWith<IllegalStateException> {
            memoryStoreFromEnv { key -> if (key == "KAIOS_MEMORY_STORE") "unknown" else null }
        }
    }
}
