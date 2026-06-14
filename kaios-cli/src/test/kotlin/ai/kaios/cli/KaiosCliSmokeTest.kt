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
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KaiosCliSmokeTest {
    @Test
    fun `run ps and inspect work against a saved mock run`() {
        val root = Files.createTempDirectory("kaios-cli-runs")
        val reportRoot = Files.createTempDirectory("kaios-cli-reports")
        val artifactRoot = Files.createTempDirectory("kaios-cli-artifacts")
        val cli = KaiosCli(FileRunSnapshotStore(root), reportRoot, artifactRoot = artifactRoot, snapshotRoot = root)

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
    fun `run out writes a markdown artifact`() {
        val workspace = Files.createTempDirectory("kaios-cli-run-out")
        val cli = cliFor(workspace)
        val artifact = workspace.resolve("artifacts/custom.md")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("run", "--out", artifact.toString(), "draft", "a", "runtime", "note"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val artifactText = Files.readString(artifact)

        assertEquals(0, code)
        assertTrue(text.contains("artifact: $artifact"))
        assertTrue(artifactText.contains("# KAI OS Run"))
        assertTrue(artifactText.contains("## Final Output"))
        assertTrue(artifactText.contains("## Process Table"))
        assertTrue(artifactText.contains("| PID | Agent | State | Tokens | Memory | Syscalls | Duration |"))

        val err = ByteArrayOutputStream()
        val secondCode = cli.run(
            arrayOf("run", "--out", artifact.toString(), "draft", "again"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, secondCode)
        assertTrue(err.toString().contains("already exists"))
    }

    @Test
    fun `export writes default artifact and protects existing files`() {
        val workspace = Files.createTempDirectory("kaios-cli-export")
        val cli = cliFor(workspace)
        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "draft", "artifact", "summary"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)
        assertEquals(0, runCode)
        assertTrue(runId != null)

        val exportOut = ByteArrayOutputStream()
        val exportCode = cli.run(arrayOf("export", runId), PrintStream(exportOut), PrintStream(ByteArrayOutputStream()))
        val artifact = workspace.resolve("artifacts/$runId.md")
        val artifactText = Files.readString(artifact)

        assertEquals(0, exportCode)
        assertTrue(exportOut.toString().contains("artifact: $artifact"))
        assertTrue(artifactText.contains("draft artifact summary"))
        assertTrue(artifactText.contains("Lifecycle Events"))

        val err = ByteArrayOutputStream()
        val secondCode = cli.run(arrayOf("export", runId), PrintStream(ByteArrayOutputStream()), PrintStream(err))

        assertEquals(1, secondCode)
        assertTrue(err.toString().contains("already exists"))

        val forcedCode = cli.run(
            arrayOf("export", runId, "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, forcedCode)
    }

    @Test
    fun `run with context file includes source summary in saved artifact`() {
        val workspace = Files.createTempDirectory("kaios-cli-context")
        val cli = cliFor(workspace)
        val context = workspace.resolve("notes.md")
        val artifact = workspace.resolve("artifacts/context.md")
        Files.writeString(context, "# Notes\nKAI context payload for a useful agent run.\n")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf(
                "run",
                "--context",
                context.toString(),
                "--out",
                artifact.toString(),
                "summarize",
                "the",
                "project",
            ),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val artifactText = Files.readString(artifact)

        assertEquals(0, code)
        assertTrue(text.contains("context: 1 file(s)"))
        assertTrue(!text.contains("KAI context payload"))
        assertTrue(artifactText.contains("Context:"))
        assertTrue(artifactText.contains("notes.md"))
        assertTrue(!artifactText.contains("KAI context payload"))
    }

    @Test
    fun `run with missing context path fails before spawning agents`() {
        val workspace = Files.createTempDirectory("kaios-cli-missing-context")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("run", "--context", workspace.resolve("missing.md").toString(), "summarize", "this"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, code)
        assertTrue(err.toString().contains("was not found"))
    }

    @Test
    fun `init writes a project config and refuses accidental overwrite`() {
        val workspace = Files.createTempDirectory("kaios-cli-init")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("init"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val configPath = workspace.resolve("kaios.json")
        val configText = Files.readString(configPath)

        assertEquals(0, code)
        assertTrue(out.toString().contains("created:"))
        assertTrue(configText.contains("\"planner\""))
        assertTrue(configText.contains("\"executor\""))
        assertTrue(configText.contains("\"validator\""))

        val err = ByteArrayOutputStream()
        val overwriteCode = cli.run(arrayOf("init"), PrintStream(ByteArrayOutputStream()), PrintStream(err))

        assertEquals(1, overwriteCode)
        assertTrue(err.toString().contains("already exists"))
    }

    @Test
    fun `init template writes a useful workflow and run auto-detects kaios config`() {
        val workspace = Files.createTempDirectory("kaios-cli-template")
        val cli = cliFor(workspace)
        val initOut = ByteArrayOutputStream()

        val initCode = cli.run(
            arrayOf("init", "--template", "research"),
            PrintStream(initOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val configText = Files.readString(workspace.resolve("kaios.json"))

        assertEquals(0, initCode)
        assertTrue(initOut.toString().contains("template: research"))
        assertTrue(configText.contains("\"researcher\""))
        assertTrue(configText.contains("\"synthesizer\""))

        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "summarize", "agent", "operating", "systems"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)

        assertEquals(0, runCode)
        assertTrue(runText.contains("config: ${workspace.resolve("kaios.json")}"))
        assertTrue(runId != null)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(arrayOf("ps", runId), PrintStream(psOut), PrintStream(ByteArrayOutputStream()))
        val psText = psOut.toString()

        assertEquals(0, psCode)
        assertTrue(psText.contains("workflow=research"))
        assertTrue(psText.contains("researcher"))
        assertTrue(psText.contains("synthesizer"))
        assertTrue(psText.contains("validator"))
    }

    @Test
    fun `run default ignores an auto-detected project config`() {
        val workspace = Files.createTempDirectory("kaios-cli-default-flag")
        val cli = cliFor(workspace)
        cli.run(arrayOf("init", "--template", "release"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))

        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "--default", "draft", "a", "release", "note"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)

        assertEquals(0, runCode)
        assertTrue(!runText.contains("config:"))
        assertTrue(runId != null)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(arrayOf("ps", runId), PrintStream(psOut), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, psCode)
        assertTrue(psOut.toString().contains("workflow=default"))
    }

    @Test
    fun `config commands validate show and list templates`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-commands")
        val cli = cliFor(workspace)
        val initCode = cli.run(
            arrayOf("init", "--template", "code-review"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, initCode)

        val validateOut = ByteArrayOutputStream()
        val validateCode = cli.run(
            arrayOf("config", "validate"),
            PrintStream(validateOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val validateText = validateOut.toString()

        assertEquals(0, validateCode)
        assertTrue(validateText.contains("status: valid"))
        assertTrue(validateText.contains("workflow: code-review"))

        val showOut = ByteArrayOutputStream()
        val showCode = cli.run(arrayOf("config", "show"), PrintStream(showOut), PrintStream(ByteArrayOutputStream()))
        val showText = showOut.toString()

        assertEquals(0, showCode)
        assertTrue(showText.contains("inspector tools=echo,file dependsOn=-"))
        assertTrue(showText.contains("inspector -> reviewer"))
        assertTrue(showText.contains("reviewer -> validator"))

        val templatesOut = ByteArrayOutputStream()
        val templatesCode = cli.run(
            arrayOf("config", "templates"),
            PrintStream(templatesOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val templatesText = templatesOut.toString()

        assertEquals(0, templatesCode)
        assertTrue(templatesText.contains("research"))
        assertTrue(templatesText.contains("code-review"))
        assertTrue(templatesText.contains("release"))
    }

    @Test
    fun `run with config executes configured workflow agents`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-run")
        val cli = cliFor(workspace)
        val config = workspace.resolve("research.json")
        Files.writeString(
            config,
            """
            {
              "name": "custom-research",
              "agents": [
                {
                  "id": "researcher",
                  "instruction": "Gather useful context for the task.",
                  "tools": ["echo", "clock"],
                  "dependsOn": []
                },
                {
                  "id": "writer",
                  "instruction": "Turn the research into a concise answer.",
                  "tools": ["echo"],
                  "dependsOn": ["researcher"]
                },
                {
                  "id": "validator",
                  "instruction": "Check the answer and mark it accepted.",
                  "tools": ["echo"],
                  "dependsOn": ["writer"]
                }
              ]
            }
            """.trimIndent(),
        )

        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "--config", config.toString(), "map", "the", "JVM", "agent", "runtime"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)

        assertEquals(0, runCode)
        assertTrue(runText.contains("config: $config"))
        assertTrue(runText.contains("validate:"))
        assertTrue(runId != null)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(arrayOf("ps", runId), PrintStream(psOut), PrintStream(ByteArrayOutputStream()))
        val psText = psOut.toString()

        assertEquals(0, psCode)
        assertTrue(psText.contains("workflow=custom-research"))
        assertTrue(psText.contains("researcher"))
        assertTrue(psText.contains("writer"))
        assertTrue(psText.contains("validator"))

        val inspectOut = ByteArrayOutputStream()
        val inspectCode = cli.run(arrayOf("inspect", runId), PrintStream(inspectOut), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, inspectCode)
        assertTrue(inspectOut.toString().contains("workflow: custom-research"))
    }

    @Test
    fun `run with config rejects unknown tools before spawning agents`() {
        val workspace = Files.createTempDirectory("kaios-cli-bad-config")
        val cli = cliFor(workspace)
        val config = workspace.resolve("bad.json")
        Files.writeString(
            config,
            """
            {
              "name": "bad",
              "agents": [
                {
                  "id": "planner",
                  "tools": ["shell"]
                }
              ]
            }
            """.trimIndent(),
        )
        val err = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("run", "--config", config.toString(), "unsafe", "task"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, code)
        assertTrue(err.toString().contains("unknown tool"))
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

    @Test
    fun `doctor reports ready for default local runtime`() {
        val root = Files.createTempDirectory("kaios-cli-doctor-runs")
        val reportRoot = Files.createTempDirectory("kaios-cli-doctor-reports")
        val artifactRoot = Files.createTempDirectory("kaios-cli-doctor-artifacts")
        val cli = KaiosCli(FileRunSnapshotStore(root), reportRoot, artifactRoot = artifactRoot, snapshotRoot = root)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("doctor"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("KAI OS doctor"))
        assertTrue(text.contains("[OK] Java runtime"))
        assertTrue(text.contains("[OK] runs directory"))
        assertTrue(text.contains("[OK] reports directory"))
        assertTrue(text.contains("[OK] artifacts directory"))
        assertTrue(text.contains("[OK] model provider: mock"))
        assertTrue(text.contains("[OK] project config"))
        assertTrue(text.contains("summary: ready"))
    }

    @Test
    fun `doctor fails on invalid provider configuration without printing secrets`() {
        val root = Files.createTempDirectory("kaios-cli-doctor-bad-provider-runs")
        val reportRoot = Files.createTempDirectory("kaios-cli-doctor-bad-provider-reports")
        val cli = KaiosCli(
            FileRunSnapshotStore(root),
            reportRoot,
            artifactRoot = Files.createTempDirectory("kaios-cli-doctor-bad-provider-artifacts"),
            snapshotRoot = root,
            env = { key ->
                mapOf(
                    "KAIOS_MODEL_PROVIDER" to "openai",
                    "OPENAI_API_KEY" to "secret-key",
                )[key]
            },
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("doctor"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(2, code)
        assertTrue(text.contains("[FAIL] model provider"))
        assertTrue(text.contains("OPENAI_MODEL is required"))
        assertTrue(!text.contains("secret-key"))
    }
}

private fun cliFor(workspace: Path): KaiosCli {
    val runs = workspace.resolve("runs")
    return KaiosCli(
        FileRunSnapshotStore(runs),
        workspace.resolve("reports"),
        artifactRoot = workspace.resolve("artifacts"),
        snapshotRoot = runs,
        workingDir = workspace,
    )
}
