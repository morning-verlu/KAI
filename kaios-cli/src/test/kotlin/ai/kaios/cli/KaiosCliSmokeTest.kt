package ai.kaios.cli

import ai.kaios.FileRunSnapshotStore
import ai.kaios.MockModelProvider
import ai.kaios.OllamaModelProvider
import ai.kaios.OpenAiCompatibleModelProvider
import ai.kaios.SQLiteMemoryStore
import ai.kaios.SessionMemoryStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun `version command prints installed cli version`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-version"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("--version"), PrintStream(out), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, code)
        assertEquals("kaios 0.1.23\n", out.toString())
    }

    @Test
    fun `help leads with three step quick start`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Quick start (3 steps):"))
        assertTrue(text.contains("kaios doctor"))
        assertTrue(text.contains("kaios analyze . --out artifacts/analysis.md --force"))
        assertTrue(text.contains("kaios run --index . --out artifacts/project.md --force \"summarize this project\""))
        assertTrue(text.contains("kaios --version"))
        assertTrue(text.contains("kaios help <command>"))
        assertTrue(text.contains("Command groups:"))
    }

    @Test
    fun `core subcommands support help flag without running work`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-subcommand-help"))
        val cases = mapOf(
            "init" to "Usage: kaios init",
            "run" to "Usage: kaios run",
            "context" to "Usage: kaios context",
            "index" to "Usage: kaios index",
            "analyze" to "Usage: kaios analyze",
            "config" to "Usage: kaios config",
            "runs" to "Usage: kaios runs",
            "ps" to "Usage: kaios ps",
            "inspect" to "Usage: kaios inspect",
            "report" to "Usage: kaios report",
            "export" to "Usage: kaios export",
            "doctor" to "Usage: kaios doctor",
        )

        cases.forEach { (command, usage) ->
            val out = ByteArrayOutputStream()
            val code = cli.run(arrayOf(command, "--help"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
            val text = out.toString()

            assertEquals(0, code)
            assertTrue(text.contains(usage), command)
            assertTrue(text.contains("Examples:"), command)
            assertTrue(text.contains("kaios help"), command)
            assertTrue(!text.contains("run_id:"), command)
        }
    }

    @Test
    fun `help command can show a specific command usage`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-command"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "run"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios run"))
        assertTrue(text.contains("Run an inspectable agent workflow"))
        assertTrue(text.contains("Examples:"))
        assertTrue(text.contains("kaios run --index . --out artifacts/project.md --force \"summarize this project\""))
        assertTrue(text.contains("No API key is required by default"))
        assertTrue(text.contains("kaios ps <run-id>"))
        assertTrue(text.contains("kaios help"))
        assertTrue(!text.contains("run_id:"))
    }

    @Test
    fun `help command help flag shows global help`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-help"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "--help"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Quick start (3 steps):"))
        assertTrue(text.contains("kaios help <command>"))
    }

    @Test
    fun `help command rejects unknown command names`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-unknown"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "missing"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown command 'missing'"))
        assertTrue(text.contains("Usage: kaios help <command>"))
    }

    @Test
    fun `run rejects unknown leading options but allows dash tasks after separator`() {
        val workspace = Files.createTempDirectory("kaios-cli-run-options")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val badCode = cli.run(
            arrayOf("run", "--bad-option", "hello"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, badCode)
        assertTrue(err.toString().contains("Unknown run option '--bad-option'"))
        assertTrue(err.toString().contains("Use -- before a task that starts with '-'"))

        val out = ByteArrayOutputStream()
        val separatorCode = cli.run(
            arrayOf("run", "--", "--write", "a", "dash-prefixed", "task"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, separatorCode)
        assertTrue(out.toString().contains("success: true"))
    }

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
        assertTrue(err.toString().contains("Use --force"))

        val forcedCode = cli.run(
            arrayOf("run", "--out", artifact.toString(), "--force", "draft", "again"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, forcedCode)
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
    fun `context command previews workspace sources`() {
        val workspace = Files.createTempDirectory("kaios-cli-context-preview")
        Files.writeString(workspace.resolve("README.md"), "# KAI OS\nUseful context.\n")
        Files.createDirectories(workspace.resolve("src"))
        Files.writeString(workspace.resolve("src/Main.kt"), "fun main() = println(\"kai\")\n")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("context", "."), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("CONTEXT"))
        assertTrue(text.contains("files: 2"))
        assertTrue(text.contains("README.md"))
        assertTrue(text.contains("src/Main.kt"))
        assertTrue(text.contains("PATH"))
        assertTrue(text.contains("CHARS"))
    }

    @Test
    fun `kaiosignore controls context preview and run context`() {
        val workspace = Files.createTempDirectory("kaios-cli-context-ignore")
        Files.writeString(workspace.resolve(".kaiosignore"), "secrets/\n*.skip.md\n")
        Files.writeString(workspace.resolve("README.md"), "# KAI OS\nPublic context.\n")
        Files.writeString(workspace.resolve("notes.skip.md"), "ignored note\n")
        Files.createDirectories(workspace.resolve("secrets"))
        Files.writeString(workspace.resolve("secrets/private.md"), "do not load\n")
        val cli = cliFor(workspace)
        val previewOut = ByteArrayOutputStream()

        val previewCode = cli.run(arrayOf("context", "."), PrintStream(previewOut), PrintStream(ByteArrayOutputStream()))
        val previewText = previewOut.toString()

        assertEquals(0, previewCode)
        assertTrue(previewText.contains("ignore: .kaiosignore (2 pattern(s))"))
        assertTrue(previewText.contains("README.md"))
        assertTrue(!previewText.contains("notes.skip.md"))
        assertTrue(!previewText.contains("secrets/private.md"))

        val artifact = workspace.resolve("artifacts/context.md")
        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "--context", ".", "--out", artifact.toString(), "summarize", "public", "context"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val artifactText = Files.readString(artifact)

        assertEquals(0, runCode)
        assertTrue(runOut.toString().contains("context: 1 file(s)"))
        assertTrue(artifactText.contains("README.md"))
        assertTrue(!artifactText.contains("notes.skip.md"))
        assertTrue(!artifactText.contains("secrets/private.md"))
    }

    @Test
    fun `generated artifacts are skipped by context and index previews`() {
        val workspace = Files.createTempDirectory("kaios-cli-generated-artifacts")
        Files.writeString(workspace.resolve("README.md"), "# KAI OS\nPublic context.\n")
        Files.createDirectories(workspace.resolve("artifacts"))
        Files.writeString(workspace.resolve("artifacts/analysis.md"), "# Generated report\nDo not feed this back.\n")
        val cli = cliFor(workspace)
        val contextOut = ByteArrayOutputStream()
        val indexOut = ByteArrayOutputStream()

        val contextCode = cli.run(arrayOf("context", "."), PrintStream(contextOut), PrintStream(ByteArrayOutputStream()))
        val indexCode = cli.run(arrayOf("index", "."), PrintStream(indexOut), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, contextCode)
        assertEquals(0, indexCode)
        assertTrue(contextOut.toString().contains("README.md"))
        assertTrue(!contextOut.toString().contains("artifacts/analysis.md"))
        assertTrue(indexOut.toString().contains("README.md"))
        assertTrue(!indexOut.toString().contains("artifacts/analysis.md"))
    }

    @Test
    fun `index command summarizes workspace shape and honors ignore rules`() {
        val workspace = Files.createTempDirectory("kaios-cli-index")
        Files.writeString(workspace.resolve(".kaiosignore"), "secrets/\n")
        Files.writeString(workspace.resolve("README.md"), "# Demo\nA small indexed project.\n")
        Files.writeString(workspace.resolve("build.gradle.kts"), "plugins { kotlin(\"jvm\") }\n")
        Files.createDirectories(workspace.resolve("src/main/kotlin"))
        Files.writeString(workspace.resolve("src/main/kotlin/App.kt"), "fun main() = println(\"kai\")\n")
        Files.createDirectories(workspace.resolve("secrets"))
        Files.writeString(workspace.resolve("secrets/private.md"), "do not index\n")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("index", "."), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("WORKSPACE INDEX"))
        assertTrue(text.contains("LANGUAGES"))
        assertTrue(text.contains("Kotlin"))
        assertTrue(text.contains("Markdown"))
        assertTrue(text.contains("README.md"))
        assertTrue(text.contains("src/main/kotlin/App.kt"))
        assertTrue(text.contains("ignore: .kaiosignore"))
        assertTrue(!text.contains("secrets/private.md"))
    }

    @Test
    fun `run with workspace index includes source map summary in artifact`() {
        val workspace = Files.createTempDirectory("kaios-cli-run-index")
        Files.writeString(workspace.resolve("README.md"), "# Indexed Project\nUseful public overview.\n")
        Files.createDirectories(workspace.resolve("src/main/kotlin"))
        Files.writeString(workspace.resolve("src/main/kotlin/App.kt"), "fun main() = println(\"kai\")\n")
        val cli = cliFor(workspace)
        val artifact = workspace.resolve("artifacts/indexed.md")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("run", "--index", ".", "--out", artifact.toString(), "summarize", "the", "project", "shape"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val artifactText = Files.readString(artifact)

        assertEquals(0, code)
        assertTrue(text.contains("index:"))
        assertTrue(text.contains("Kotlin:1"))
        assertTrue(artifactText.contains("Workspace Index:"))
        assertTrue(artifactText.contains("README.md"))
        assertTrue(artifactText.contains("src/main/kotlin/App.kt"))
        assertTrue(!artifactText.contains("Useful public overview."))
    }

    @Test
    fun `analyze command renders deterministic workspace report`() {
        val workspace = Files.createTempDirectory("kaios-cli-analyze")
        Files.writeString(workspace.resolve("README.md"), "# Demo\nUseful public overview.\n")
        Files.writeString(workspace.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n")
        Files.createDirectories(workspace.resolve(".github/workflows"))
        Files.writeString(workspace.resolve(".github/workflows/ci.yml"), "name: CI\n")
        Files.createDirectories(workspace.resolve("src/main/kotlin"))
        Files.writeString(workspace.resolve("src/main/kotlin/App.kt"), "fun main() = println(\"kai\")\n")
        Files.createDirectories(workspace.resolve("src/test/kotlin"))
        Files.writeString(workspace.resolve("src/test/kotlin/AppTest.kt"), "class AppTest\n")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("analyze", "."), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("# KAI OS Workspace Analysis"))
        assertTrue(text.contains("Gradle Kotlin DSL project detected"))
        assertTrue(text.contains("Kotlin is present"))
        assertTrue(text.contains("GitHub Actions workflow files are present"))
        assertTrue(text.contains("1 test file(s) found"))
        assertTrue(text.contains("kaios run --index . --context README.md"))
        assertTrue(!text.contains("Useful public overview."))
    }

    @Test
    fun `analyze command renders structured json`() {
        val workspace = Files.createTempDirectory("kaios-cli-analyze-json")
        Files.writeString(workspace.resolve("README.md"), "# Demo\nUseful public overview.\n")
        Files.createDirectories(workspace.resolve("src/main/kotlin"))
        Files.writeString(workspace.resolve("src/main/kotlin/App.kt"), "fun main() = println(\"kai\")\n")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("analyze", ".", "--format", "json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()
        val root = Json.parseToJsonElement(text).jsonObject
        val summary = root.getValue("summary").jsonObject
        val languages = root.getValue("languages").jsonArray

        assertEquals(0, code)
        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(2, summary.getValue("files").jsonPrimitive.int)
        assertTrue(languages.any { language ->
            language.jsonObject.getValue("language").jsonPrimitive.content == "Kotlin"
        })
        assertTrue(root.getValue("suggestedCommands").jsonArray.isNotEmpty())
        assertTrue(!text.contains("Useful public overview."))
    }

    @Test
    fun `analyze out writes report and protects existing files`() {
        val workspace = Files.createTempDirectory("kaios-cli-analyze-out")
        Files.writeString(workspace.resolve("README.md"), "# Demo\n")
        Files.createDirectories(workspace.resolve("src/main/kotlin"))
        Files.writeString(workspace.resolve("src/main/kotlin/App.kt"), "fun main() = println(\"kai\")\n")
        val cli = cliFor(workspace)
        val report = workspace.resolve("artifacts/analysis.md")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("analyze", ".", "--out", report.toString()),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val reportText = Files.readString(report)

        assertEquals(0, code)
        assertTrue(out.toString().contains("analysis: $report"))
        assertTrue(out.toString().contains("format: markdown"))
        assertTrue(reportText.contains("# KAI OS Workspace Analysis"))
        assertTrue(reportText.contains("## Suggested KAI OS Commands"))

        val err = ByteArrayOutputStream()
        val secondCode = cli.run(
            arrayOf("analyze", ".", "--out", report.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, secondCode)
        assertTrue(err.toString().contains("already exists"))

        val forcedCode = cli.run(
            arrayOf("analyze", ".", "--out", report.toString(), "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, forcedCode)
    }

    @Test
    fun `analyze json out writes parseable report`() {
        val workspace = Files.createTempDirectory("kaios-cli-analyze-json-out")
        Files.writeString(workspace.resolve("README.md"), "# Demo\n")
        val cli = cliFor(workspace)
        val report = workspace.resolve("artifacts/analysis.json")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("analyze", ".", "--json", "--out", report.toString()),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val reportText = Files.readString(report)
        val root = Json.parseToJsonElement(reportText).jsonObject

        assertEquals(0, code)
        assertTrue(out.toString().contains("format: json"))
        assertEquals(1, root.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals(1, root.getValue("summary").jsonObject.getValue("files").jsonPrimitive.int)
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
    fun `config validation accepts allowlisted http syscall tool`() {
        val workspace = Files.createTempDirectory("kaios-cli-http-config")
        val cli = cliFor(workspace)
        val config = workspace.resolve("http.json")
        Files.writeString(
            config,
            """
            {
              "name": "http-research",
              "agents": [
                {
                  "id": "researcher",
                  "instruction": "Fetch allowlisted project evidence.",
                  "tools": ["http"],
                  "dependsOn": []
                }
              ]
            }
            """.trimIndent(),
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("config", "validate", "--config", config.toString()),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, code)
        assertTrue(out.toString().contains("status: valid"))
    }

    @Test
    fun `config show includes retry policy`() {
        val workspace = Files.createTempDirectory("kaios-cli-retry-config")
        val cli = cliFor(workspace)
        val config = workspace.resolve("retry.json")
        Files.writeString(
            config,
            """
            {
              "name": "retry-workflow",
              "agents": [
                {
                  "id": "worker",
                  "instruction": "Retry transient failures.",
                  "tools": ["echo"],
                  "retries": 2
                }
              ]
            }
            """.trimIndent(),
        )
        val validateOut = ByteArrayOutputStream()
        val validateCode = cli.run(
            arrayOf("config", "validate", "--config", config.toString()),
            PrintStream(validateOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val showOut = ByteArrayOutputStream()
        val showCode = cli.run(
            arrayOf("config", "show", "--config", config.toString()),
            PrintStream(showOut),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, validateCode)
        assertEquals(0, showCode)
        assertTrue(showOut.toString().contains("worker tools=echo dependsOn=- retries=2"))
    }

    @Test
    fun `config validation rejects invalid retry policy`() {
        val workspace = Files.createTempDirectory("kaios-cli-bad-retry-config")
        val cli = cliFor(workspace)
        val config = workspace.resolve("retry.json")
        Files.writeString(
            config,
            """
            {
              "name": "bad-retry",
              "agents": [
                {
                  "id": "worker",
                  "tools": ["echo"],
                  "retries": 11
                }
              ]
            }
            """.trimIndent(),
        )
        val err = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("config", "validate", "--config", config.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, code)
        assertTrue(err.toString().contains("retries must be between 0 and 10"))
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
        assertTrue(text.contains("[OK] http syscall: disabled"))
        assertTrue(text.contains("[OK] project config"))
        assertTrue(text.contains("summary: ready"))
        assertTrue(text.contains("next:"))
        assertTrue(text.contains("kaios analyze . --out artifacts/analysis.md --force"))
        assertTrue(text.contains("kaios run --index ."))
        assertTrue(text.contains("--force"))
        assertTrue(text.contains("\"summarize this project\""))
    }

    @Test
    fun `doctor reports configured http allowlist without secrets`() {
        val workspace = Files.createTempDirectory("kaios-cli-doctor-http")
        val cli = KaiosCli(
            FileRunSnapshotStore(workspace.resolve("runs")),
            workspace.resolve("reports"),
            artifactRoot = workspace.resolve("artifacts"),
            snapshotRoot = workspace.resolve("runs"),
            workingDir = workspace,
            env = { key ->
                mapOf("KAIOS_HTTP_ALLOWLIST" to "example.com,https://api.example.com/v1")[key]
            },
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("doctor"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("[OK] http syscall: 2 allowlist rule(s)"))
        assertTrue(text.contains("example.com"))
        assertTrue(text.contains("https://api.example.com/v1"))
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
