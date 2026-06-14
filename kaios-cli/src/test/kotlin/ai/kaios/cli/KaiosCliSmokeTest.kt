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
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
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
        assertEquals("kaios 0.1.57\n", out.toString())
    }

    @Test
    fun `empty invocation shows quick start without looking broken`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-empty"))
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val code = cli.run(emptyArray(), PrintStream(out), PrintStream(err))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Quick start (3 steps):"))
        assertTrue(text.contains("kaios demo"))
        assertEquals("", err.toString())
    }

    @Test
    fun `help leads with three step quick start`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Quick start (3 steps):"))
        assertTrue(text.contains("kaios demo"))
        assertTrue(text.contains("kaios setup --ci"))
        assertTrue(text.contains("kaios verify"))
        assertTrue(text.contains("kaios run --index . --context README.md --out artifact.md --trace-out trace.json --force \"task\""))
        assertTrue(text.contains("kaios evidence latest"))
        assertTrue(text.contains("kaios --version"))
        assertTrue(text.contains("kaios help <command>"))
        assertTrue(text.contains("Command groups:"))
    }

    @Test
    fun `core subcommands support help flag without running work`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-subcommand-help"))
        val cases = mapOf(
            "setup" to "Usage: kaios setup",
            "verify" to "Usage: kaios verify",
            "init" to "Usage: kaios init",
            "demo" to "Usage: kaios demo",
            "run" to "Usage: kaios run",
            "context" to "Usage: kaios context",
            "index" to "Usage: kaios index",
            "analyze" to "Usage: kaios analyze",
            "config" to "Usage: kaios config",
            "runs" to "Usage: kaios runs",
            "ps" to "Usage: kaios ps",
            "inspect" to "Usage: kaios inspect",
            "trace" to "Usage: kaios trace",
            "capsule" to "Usage: kaios capsule",
            "replay" to "Usage: kaios replay",
            "diff" to "Usage: kaios diff",
            "evidence" to "Usage: kaios evidence",
            "report" to "Usage: kaios report",
            "export" to "Usage: kaios export",
            "doctor" to "Usage: kaios doctor",
            "bug-report" to "Usage: kaios bug-report",
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
    fun `demo runs no key workflow and prints process table plus artifacts`() {
        val workspace = Files.createTempDirectory("kaios-cli-demo")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("demo"), PrintStream(out), PrintStream(err))
        val text = out.toString()
        val runId = Regex("run_id: (\\S+)").find(text)?.groupValues?.get(1)
        val tracePath = Regex("trace: (\\S+)").find(text)?.groupValues?.get(1)?.let(Paths::get)
        val artifactPath = Regex("artifact: (\\S+)").find(text)?.groupValues?.get(1)?.let(Paths::get)

        assertEquals(0, code)
        assertEquals("", err.toString())
        assertTrue(text.contains("KAI OS demo"))
        assertTrue(text.contains("provider: mock"))
        assertTrue(text.contains("processes:"))
        assertTrue(text.contains("planner"))
        assertTrue(text.contains("executor"))
        assertTrue(text.contains("validator"))
        assertTrue(text.contains("kaios ps latest"))
        assertTrue(text.contains("kaios inspect latest"))
        assertTrue(text.contains("kaios trace latest --json"))
        assertTrue(text.contains("kaios evidence latest"))
        assertTrue(runId != null)
        assertTrue(tracePath != null)
        assertTrue(artifactPath != null)
        assertTrue(Files.exists(tracePath))
        assertTrue(Files.exists(artifactPath))

        val traceJson = Json.parseToJsonElement(Files.readString(tracePath)).jsonObject
        assertEquals("kaios.process-trace/v1", traceJson.getValue("schema").jsonPrimitive.content)
        assertEquals(3, traceJson.getValue("metrics").jsonObject.getValue("processCount").jsonPrimitive.int)
    }

    @Test
    fun `demo rejects arguments with help pointer`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-demo-args"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("demo", "extra"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Demo does not accept arguments."))
        assertTrue(text.contains("Usage: kaios demo"))
        assertTrue(text.contains("Run 'kaios help demo' for examples."))
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
        assertTrue(text.contains("kaios ps latest"))
        assertTrue(text.contains("kaios trace latest"))
        assertTrue(text.contains("kaios help"))
        assertTrue(!text.contains("run_id:"))
    }

    @Test
    fun `trace help documents contract check mode`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-trace"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "trace"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios trace"))
        assertTrue(text.contains("kaios trace latest --check"))
        assertTrue(text.contains("validate the trace contract"))
    }

    @Test
    fun `capsule help documents portable run contract`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-capsule"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "capsule"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios capsule"))
        assertTrue(text.contains("portable run capsule"))
        assertTrue(text.contains("kaios.run-capsule/v1"))
        assertTrue(text.contains("--file"))
        assertTrue(text.contains("does not re-run agents"))
    }

    @Test
    fun `replay help documents offline capsule replay`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-replay"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "replay"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios replay"))
        assertTrue(text.contains("kaios.run-replay/v1"))
        assertTrue(text.contains("never calls a model provider"))
        assertTrue(text.contains("rebuilt trace matches the embedded trace"))
    }

    @Test
    fun `diff help documents offline capsule comparison`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-diff"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "diff"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios diff"))
        assertTrue(text.contains("kaios.run-diff/v1"))
        assertTrue(text.contains("ignores run ids, timestamps, and duration noise"))
        assertTrue(text.contains("--check exits 1"))
    }

    @Test
    fun `evidence help documents one command audit gate`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-evidence"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "evidence"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios evidence"))
        assertTrue(text.contains("kaios.evidence/v1"))
        assertTrue(text.contains("packaging, validating, replaying"))
        assertTrue(text.contains("--check exits 1"))
    }

    @Test
    fun `setup help documents safe project bootstrap`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-setup"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "setup"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios setup"))
        assertTrue(text.contains("Bootstrap a project workflow"))
        assertTrue(text.contains("kaios setup --ci"))
        assertTrue(text.contains("kaios.setup/v1"))
        assertTrue(text.contains("Existing config and CI files are kept"))
    }

    @Test
    fun `verify help documents readiness gate`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-verify"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "verify"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios verify"))
        assertTrue(text.contains("one-command readiness and evidence gate"))
        assertTrue(text.contains("deterministic mock smoke workflow"))
        assertTrue(text.contains("--evidence"))
        assertTrue(text.contains("--evidence-out"))
        assertTrue(text.contains("baseline evidence differs"))
        assertTrue(text.contains("kaios.verify/v1"))
    }

    @Test
    fun `bug report help documents safe support report`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-bug-report"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "bug-report"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("Usage: kaios bug-report"))
        assertTrue(text.contains("safe support report"))
        assertTrue(text.contains("kaios.bug-report/v1"))
        assertTrue(text.contains("does not print API keys"))
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
        assertTrue(text.contains("Run 'kaios help' for available commands."))
        assertTrue(text.contains("Usage: kaios help <command>"))
    }

    @Test
    fun `help command suggests close command names`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-help-suggest"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "analyse"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown command 'analyse'"))
        assertTrue(text.contains("Did you mean 'kaios help analyze'?"))
        assertTrue(text.contains("Usage: kaios help <command>"))
    }

    @Test
    fun `unknown command points back to available commands`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-unknown"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("wat"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown command 'wat'"))
        assertTrue(text.contains("Run 'kaios help' for available commands."))
        assertTrue(text.contains("Quick start (3 steps):"))
    }

    @Test
    fun `unknown command suggests close command names`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-unknown-suggest"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("analyse", "."), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown command 'analyse'"))
        assertTrue(text.contains("Did you mean 'kaios analyze'?"))
        assertTrue(text.contains("Run 'kaios help' for available commands."))
    }

    @Test
    fun `missing run task points to command examples`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-missing-task"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("run"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Task cannot be blank."))
        assertTrue(text.contains("Usage: kaios run"))
        assertTrue(text.contains("Run 'kaios help run' for examples."))
    }

    @Test
    fun `missing run id points to command examples`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-missing-run-id"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("ps"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Run id is required."))
        assertTrue(text.contains("Usage: kaios ps <run-id|latest>"))
        assertTrue(text.contains("Run 'kaios help ps' for examples."))
    }

    @Test
    fun `fixed arity commands reject unexpected arguments`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-extra-args"))
        val cases = listOf(
            arrayOf("runs", "extra") to "Unexpected runs argument 'extra'.",
            arrayOf("doctor", "extra") to "Unexpected doctor argument 'extra'.",
            arrayOf("version", "extra") to "Unexpected version argument 'extra'.",
            arrayOf("ps", "latest", "extra") to "Unexpected ps argument 'extra'.",
            arrayOf("inspect", "latest", "extra") to "Unexpected inspect argument 'extra'.",
            arrayOf("capsule", "latest", "extra") to "Unexpected capsule argument 'extra'.",
            arrayOf("evidence", "latest", "extra") to "Unexpected evidence argument 'extra'.",
            arrayOf("report", "latest", "extra") to "Unexpected report argument 'extra'.",
        )

        cases.forEach { (args, message) ->
            val err = ByteArrayOutputStream()
            val code = cli.run(args, PrintStream(ByteArrayOutputStream()), PrintStream(err))
            val text = err.toString()

            assertEquals(1, code, args.joinToString(" "))
            assertTrue(text.contains(message), args.joinToString(" "))
            assertTrue(text.contains("Usage: kaios"), args.joinToString(" "))
            assertTrue(text.contains("Run 'kaios help"), args.joinToString(" "))
        }
    }

    @Test
    fun `latest run id points to newest saved snapshot`() {
        val workspace = Files.createTempDirectory("kaios-cli-latest")
        val cli = cliFor(workspace)

        val firstOut = ByteArrayOutputStream()
        val firstCode = cli.run(
            arrayOf("run", "first", "agent", "process"),
            PrintStream(firstOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val firstRunId = Regex("run_id: (\\S+)").find(firstOut.toString())?.groupValues?.get(1)

        val secondOut = ByteArrayOutputStream()
        val secondCode = cli.run(
            arrayOf("run", "second", "agent", "process"),
            PrintStream(secondOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val secondRunId = Regex("run_id: (\\S+)").find(secondOut.toString())?.groupValues?.get(1)

        assertEquals(0, firstCode)
        assertEquals(0, secondCode)
        assertTrue(firstRunId != null)
        assertTrue(secondRunId != null)
        Files.setLastModifiedTime(workspace.resolve("runs/$firstRunId.json"), FileTime.fromMillis(1_000))
        Files.setLastModifiedTime(workspace.resolve("runs/$secondRunId.json"), FileTime.fromMillis(2_000))

        val runsOut = ByteArrayOutputStream()
        val runsCode = cli.run(arrayOf("runs"), PrintStream(runsOut), PrintStream(ByteArrayOutputStream()))
        val runsText = runsOut.toString()
        assertEquals(0, runsCode)
        assertTrue(runsText.contains("ALIAS"))
        assertTrue(runsText.indexOf(secondRunId) < runsText.indexOf(firstRunId))
        assertTrue(runsText.lineSequence().any { line ->
            line.contains(secondRunId) && line.contains("latest") && line.contains("second agent process")
        })
        assertTrue(runsText.lineSequence().any { line ->
            line.contains(firstRunId) && !line.contains("latest") && line.contains("first agent process")
        })

        val runsJsonOut = ByteArrayOutputStream()
        val runsJsonCode = cli.run(arrayOf("runs", "--json"), PrintStream(runsJsonOut), PrintStream(ByteArrayOutputStream()))
        val runsJson = Json.parseToJsonElement(runsJsonOut.toString()).jsonObject
        val runs = runsJson.getValue("runs").jsonArray
        val newestRun = runs.first().jsonObject

        assertEquals(0, runsJsonCode)
        assertEquals("kaios.runs/v1", runsJson.getValue("schema").jsonPrimitive.content)
        assertEquals(2, runsJson.getValue("count").jsonPrimitive.int)
        assertEquals(secondRunId, runsJson.getValue("latestRunId").jsonPrimitive.content)
        assertEquals(secondRunId, newestRun.getValue("runId").jsonPrimitive.content)
        assertEquals("latest", newestRun.getValue("alias").jsonPrimitive.content)
        assertEquals("success", newestRun.getValue("status").jsonPrimitive.content)
        assertEquals(3, newestRun.getValue("processCount").jsonPrimitive.int)
        assertTrue(newestRun.getValue("tokenTotal").jsonPrimitive.int > 0)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(arrayOf("ps", "latest"), PrintStream(psOut), PrintStream(ByteArrayOutputStream()))
        assertEquals(0, psCode)
        assertTrue(psOut.toString().contains("RUN $secondRunId"))

        val inspectOut = ByteArrayOutputStream()
        val inspectCode = cli.run(arrayOf("inspect", "latest"), PrintStream(inspectOut), PrintStream(ByteArrayOutputStream()))
        assertEquals(0, inspectCode)
        assertTrue(inspectOut.toString().contains("task: second agent process"))

        val traceOut = ByteArrayOutputStream()
        val traceCode = cli.run(arrayOf("trace", "latest", "--json"), PrintStream(traceOut), PrintStream(ByteArrayOutputStream()))
        val traceJson = Json.parseToJsonElement(traceOut.toString()).jsonObject
        assertEquals(0, traceCode)
        assertEquals(secondRunId, traceJson.getValue("runId").jsonPrimitive.content)

        val capsuleJsonOut = ByteArrayOutputStream()
        val capsuleJsonCode = cli.run(arrayOf("capsule", "latest", "--json"), PrintStream(capsuleJsonOut), PrintStream(ByteArrayOutputStream()))
        val capsuleJson = Json.parseToJsonElement(capsuleJsonOut.toString()).jsonObject
        assertEquals(0, capsuleJsonCode)
        assertEquals("kaios.run-capsule/v1", capsuleJson.getValue("schema").jsonPrimitive.content)
        assertEquals(secondRunId, capsuleJson.getValue("run").jsonObject.getValue("runId").jsonPrimitive.content)

        val reportOut = ByteArrayOutputStream()
        val reportCode = cli.run(arrayOf("report", "latest"), PrintStream(reportOut), PrintStream(ByteArrayOutputStream()))
        assertEquals(0, reportCode)
        assertTrue(Files.exists(workspace.resolve("reports/$secondRunId.html")))

        val artifact = workspace.resolve("artifacts/latest.md")
        val exportOut = ByteArrayOutputStream()
        val exportCode = cli.run(
            arrayOf("export", "latest", "--out", artifact.toString()),
            PrintStream(exportOut),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, exportCode)
        assertTrue(exportOut.toString().contains("artifact: $artifact"))
        assertTrue(Files.readString(artifact).contains("second agent process"))
    }

    @Test
    fun `latest run id reports friendly error when no snapshots exist`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-latest-empty"))
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("ps", "latest"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Run snapshot 'latest' was not found."))
        assertTrue(text.contains("No run snapshots are available yet."))
        assertTrue(text.contains("Run 'kaios demo' to create a no-key sample run."))
        assertTrue(text.contains("Run 'kaios setup --ci' to create a project workflow."))
        assertTrue(text.contains("Run 'kaios verify' to create an inspectable project run."))
        assertTrue(!text.contains("Run 'kaios run \"task\"' to create your own run."))
    }

    @Test
    fun `empty runs list suggests no key demo first`() {
        val cli = cliFor(Files.createTempDirectory("kaios-cli-runs-empty"))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("runs"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("No run snapshots found."))
        assertTrue(text.contains("Run 'kaios demo' to create a no-key sample run."))
        assertTrue(text.contains("Run 'kaios setup --ci' to create a project workflow."))
        assertTrue(text.contains("Run 'kaios verify' to create an inspectable project run."))
        assertTrue(!text.contains("Run 'kaios run \"task\"' to create your own run."))

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(arrayOf("runs", "--format", "json"), PrintStream(jsonOut), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(jsonOut.toString()).jsonObject

        assertEquals(0, jsonCode)
        assertEquals("kaios.runs/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals(0, json.getValue("count").jsonPrimitive.int)
        assertEquals("null", json.getValue("latestRunId").toString())
        assertEquals(0, json.getValue("runs").jsonArray.size)
    }

    @Test
    fun `missing run snapshot points to runs and recent ids`() {
        val root = Files.createTempDirectory("kaios-cli-missing-snapshot")
        val reportRoot = Files.createTempDirectory("kaios-cli-missing-snapshot-reports")
        val artifactRoot = Files.createTempDirectory("kaios-cli-missing-snapshot-artifacts")
        val cli = KaiosCli(FileRunSnapshotStore(root), reportRoot, artifactRoot = artifactRoot, snapshotRoot = root)
        val emptyErr = ByteArrayOutputStream()

        val emptyCode = cli.run(arrayOf("ps", "run-missing"), PrintStream(ByteArrayOutputStream()), PrintStream(emptyErr))
        val emptyText = emptyErr.toString()

        assertEquals(1, emptyCode)
        assertTrue(emptyText.contains("Run snapshot 'run-missing' was not found"))
        assertTrue(emptyText.contains("No run snapshots are available yet."))
        assertTrue(emptyText.contains("Run 'kaios demo' to create a no-key sample run."))
        assertTrue(emptyText.contains("Run 'kaios setup --ci' to create a project workflow."))
        assertTrue(emptyText.contains("Run 'kaios verify' to create an inspectable project run."))
        assertTrue(!emptyText.contains("Run 'kaios run \"task\"' to create your own run."))

        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "summarize", "runtime", "state"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)
        assertEquals(0, runCode)
        assertTrue(runId != null)

        val missingErr = ByteArrayOutputStream()
        val missingCode = cli.run(arrayOf("inspect", "run-unknown"), PrintStream(ByteArrayOutputStream()), PrintStream(missingErr))
        val missingText = missingErr.toString()

        assertEquals(1, missingCode)
        assertTrue(missingText.contains("Run snapshot 'run-unknown' was not found"))
        assertTrue(missingText.contains("Run 'kaios runs' to list saved run ids."))
        assertTrue(missingText.contains("Saved runs:"))
        assertTrue(missingText.contains(runId))
        assertTrue(missingText.contains("summarize runtime state"))
    }

    @Test
    fun `missing run snapshot prints multiline task summaries as one line`() {
        val workspace = Files.createTempDirectory("kaios-cli-missing-snapshot-index")
        Files.writeString(workspace.resolve("README.md"), "# Indexed Project\nUseful public overview.\n")
        val cli = cliFor(workspace)
        val runOut = ByteArrayOutputStream()

        val runCode = cli.run(
            arrayOf("run", "--index", ".", "summarize", "indexed", "project"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runText = runOut.toString()
        val runId = Regex("run_id: (\\S+)").find(runText)?.groupValues?.get(1)
        assertEquals(0, runCode)
        assertTrue(runId != null)

        val missingErr = ByteArrayOutputStream()
        val missingCode = cli.run(arrayOf("ps", "run-unknown"), PrintStream(ByteArrayOutputStream()), PrintStream(missingErr))
        val missingText = missingErr.toString()

        assertEquals(1, missingCode)
        assertTrue(missingText.contains(runId))
        assertTrue(missingText.contains("Workspace Index:"))
        assertTrue(!missingText.contains("\nWorkspace Index:"))
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
        assertTrue(err.toString().contains("Run 'kaios help run' for examples."))

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
        assertTrue(runText.contains("next:"))
        assertTrue(runText.contains("kaios ps latest"))
        assertTrue(runText.contains("kaios inspect latest"))
        assertTrue(runText.contains("kaios trace latest"))
        assertTrue(runText.contains("kaios evidence latest"))
        assertTrue(runText.contains("kaios report latest"))
        assertTrue(runText.contains("kaios export latest"))
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

        val traceOut = ByteArrayOutputStream()
        val traceCode = cli.run(
            arrayOf("trace", runId),
            PrintStream(traceOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val traceText = traceOut.toString()

        assertEquals(0, traceCode)
        assertTrue(traceText.contains("KAI PROCESS TRACE"))
        assertTrue(traceText.contains("schema: kaios.process-trace/v1"))
        assertTrue(traceText.contains("<input> -> planner(pid=1) -> executor(pid=2) -> validator(pid=3)"))
        assertTrue(traceText.contains("event_counts:"))
        assertTrue(traceText.contains("TOOL_CALLED"))
        assertTrue(traceText.contains("timeline:"))

        val traceJsonOut = ByteArrayOutputStream()
        val traceJsonCode = cli.run(
            arrayOf("trace", runId, "--json"),
            PrintStream(traceJsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val traceJson = Json.parseToJsonElement(traceJsonOut.toString()).jsonObject

        assertEquals(0, traceJsonCode)
        assertEquals("kaios.process-trace/v1", traceJson.getValue("schema").jsonPrimitive.content)
        assertEquals(3, traceJson.getValue("metrics").jsonObject.getValue("processCount").jsonPrimitive.int)
        assertEquals(3, traceJson.getValue("path").jsonArray.size)
        assertEquals(3, traceJson.getValue("processes").jsonArray.size)

        val traceCheckOut = ByteArrayOutputStream()
        val traceCheckCode = cli.run(
            arrayOf("trace", runId, "--check"),
            PrintStream(traceCheckOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val traceCheckText = traceCheckOut.toString()

        assertEquals(0, traceCheckCode)
        assertTrue(traceCheckText.contains("schema: kaios.process-trace/v1"))
        assertTrue(traceCheckText.contains("status: valid"))
        assertTrue(traceCheckText.contains("processes: 3"))

        val tracePath = artifactRoot.resolve("$runId-trace.json")
        val traceFileOut = ByteArrayOutputStream()
        val traceFileCode = cli.run(
            arrayOf("trace", runId, "--format", "json", "--out", tracePath.toString()),
            PrintStream(traceFileOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val traceFileJson = Json.parseToJsonElement(Files.readString(tracePath)).jsonObject

        assertEquals(0, traceFileCode)
        assertTrue(traceFileOut.toString().contains("trace: $tracePath"))
        assertTrue(traceFileOut.toString().contains("format: json"))
        assertEquals("kaios.process-trace/v1", traceFileJson.getValue("schema").jsonPrimitive.content)

        val traceFileErr = ByteArrayOutputStream()
        val protectedTraceCode = cli.run(
            arrayOf("trace", runId, "--json", "--out", tracePath.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(traceFileErr),
        )

        assertEquals(1, protectedTraceCode)
        assertTrue(traceFileErr.toString().contains("already exists"))
        assertTrue(traceFileErr.toString().contains("Use --force"))

        val forcedTraceCode = cli.run(
            arrayOf("trace", runId, "--json", "--out", tracePath.toString(), "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, forcedTraceCode)

        val runsOut = ByteArrayOutputStream()
        val runsCode = cli.run(
            arrayOf("runs"),
            PrintStream(runsOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runsText = runsOut.toString()

        assertEquals(0, runsCode)
        assertTrue(runsText.contains("ALIAS"))
        assertTrue(runsText.contains("latest"))
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
    fun `trace check rejects invalid saved process trace contract`() {
        val root = Files.createTempDirectory("kaios-cli-trace-check-invalid")
        val cli = KaiosCli(
            FileRunSnapshotStore(root),
            Files.createTempDirectory("kaios-cli-trace-check-invalid-reports"),
            artifactRoot = Files.createTempDirectory("kaios-cli-trace-check-invalid-artifacts"),
            snapshotRoot = root,
        )
        Files.writeString(
            root.resolve("run-corrupt.json"),
            """
            {
              "runId": "run-corrupt",
              "workflowName": "default",
              "task": "corrupt trace",
              "success": true,
              "finalOutput": "done",
              "processes": [
                {
                  "pid": 1,
                  "agent": "planner",
                  "state": "SUCCEEDED",
                  "tokens": -1,
                  "inputTokens": 1,
                  "outputTokens": 1,
                  "contextSize": 0,
                  "syscallCount": 0,
                  "durationMillis": 0
                }
              ],
              "events": [
                {
                  "timestamp": "not-a-time",
                  "pid": 99,
                  "agent": "ghost",
                  "type": "STARTED",
                  "message": "started"
                }
              ]
            }
            """.trimIndent(),
        )

        val err = ByteArrayOutputStream()
        val code = cli.run(arrayOf("trace", "run-corrupt", "--check"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(2, code)
        assertTrue(text.contains("status: invalid"))
        assertTrue(text.contains("processes[0].tokens must be non-negative."))
        assertTrue(text.contains("processes[0].tokens must equal inputTokens + outputTokens."))
        assertTrue(text.contains("events[0].timestamp must be ISO-8601."))
        assertTrue(text.contains("events[0].pid must reference a process pid."))

        val capsuleErr = ByteArrayOutputStream()
        val capsuleCode = cli.run(arrayOf("capsule", "run-corrupt", "--check"), PrintStream(ByteArrayOutputStream()), PrintStream(capsuleErr))
        val capsuleText = capsuleErr.toString()

        assertEquals(2, capsuleCode)
        assertTrue(capsuleText.contains("status: invalid"))
        assertTrue(capsuleText.contains("trace: processes[0].tokens must be non-negative."))
        assertTrue(capsuleText.contains("trace: events[0].pid must reference a process pid."))
    }

    @Test
    fun `capsule writes portable run evidence and validates contract`() {
        val workspace = Files.createTempDirectory("kaios-cli-capsule")
        val cli = cliFor(workspace)
        val runOut = ByteArrayOutputStream()
        val runCode = cli.run(
            arrayOf("run", "capture", "portable", "agent", "evidence"),
            PrintStream(runOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val runId = Regex("run_id: (\\S+)").find(runOut.toString())?.groupValues?.get(1)
        assertEquals(0, runCode)
        assertTrue(runId != null)

        val capsuleOut = ByteArrayOutputStream()
        val capsuleCode = cli.run(arrayOf("capsule", runId), PrintStream(capsuleOut), PrintStream(ByteArrayOutputStream()))
        val capsulePath = workspace.resolve("capsules/$runId.capsule.json")
        val capsuleText = capsuleOut.toString()
        val capsuleJson = Json.parseToJsonElement(Files.readString(capsulePath)).jsonObject
        val run = capsuleJson.getValue("run").jsonObject
        val provenance = capsuleJson.getValue("provenance").jsonObject
        val replay = capsuleJson.getValue("replay").jsonObject
        val validation = capsuleJson.getValue("validation").jsonObject
        val snapshot = capsuleJson.getValue("snapshot").jsonObject
        val trace = capsuleJson.getValue("trace").jsonObject

        assertEquals(0, capsuleCode)
        assertTrue(capsuleText.contains("capsule: $capsulePath"))
        assertTrue(capsuleText.contains("schema: kaios.run-capsule/v1"))
        assertTrue(capsuleText.contains("valid: true"))
        assertTrue(capsuleText.contains("kaios replay --file $capsulePath"))
        assertEquals("kaios.run-capsule/v1", capsuleJson.getValue("schema").jsonPrimitive.content)
        assertEquals("0.1.57", capsuleJson.getValue("version").jsonPrimitive.content)
        assertEquals(runId, run.getValue("runId").jsonPrimitive.content)
        assertEquals(3, run.getValue("processCount").jsonPrimitive.int)
        assertEquals(runId, snapshot.getValue("runId").jsonPrimitive.content)
        assertEquals(runId, trace.getValue("runId").jsonPrimitive.content)
        assertTrue(validation.getValue("valid").jsonPrimitive.content == "true")
        assertEquals(64, provenance.getValue("snapshotSha256").jsonPrimitive.content.length)
        assertEquals(64, provenance.getValue("embeddedSnapshotSha256").jsonPrimitive.content.length)
        assertEquals(64, provenance.getValue("traceSha256").jsonPrimitive.content.length)
        assertTrue(replay.getValue("commands").jsonArray.any { command ->
            command.jsonPrimitive.content == "kaios trace $runId --check"
        })
        assertTrue(replay.getValue("commands").jsonArray.any { command ->
            command.jsonPrimitive.content == "kaios replay --file <capsule.json>"
        })

        val checkOut = ByteArrayOutputStream()
        val checkCode = cli.run(arrayOf("capsule", runId, "--check"), PrintStream(checkOut), PrintStream(ByteArrayOutputStream()))
        assertEquals(0, checkCode)
        assertTrue(checkOut.toString().contains("status: valid"))

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(arrayOf("capsule", "latest", "--json"), PrintStream(jsonOut), PrintStream(ByteArrayOutputStream()))
        val stdoutJson = Json.parseToJsonElement(jsonOut.toString()).jsonObject
        assertEquals(0, jsonCode)
        assertEquals(runId, stdoutJson.getValue("run").jsonObject.getValue("runId").jsonPrimitive.content)

        val err = ByteArrayOutputStream()
        val protectedCode = cli.run(arrayOf("capsule", runId), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        assertEquals(1, protectedCode)
        assertTrue(err.toString().contains("already exists"))
        assertTrue(err.toString().contains("Use --force"))

        val forcedCode = cli.run(arrayOf("capsule", runId, "--force"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
        assertEquals(0, forcedCode)

        val customPath = workspace.resolve("artifacts/custom.capsule.json")
        val customOut = ByteArrayOutputStream()
        val customCode = cli.run(
            arrayOf("capsule", runId, "--out", customPath.toString()),
            PrintStream(customOut),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, customCode)
        assertTrue(customOut.toString().contains("capsule: $customPath"))
        assertTrue(Files.exists(customPath))

        Files.delete(workspace.resolve("runs/$runId.json"))

        val fileCheckOut = ByteArrayOutputStream()
        val fileCheckCode = cli.run(
            arrayOf("capsule", "--file", customPath.toString(), "--check"),
            PrintStream(fileCheckOut),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, fileCheckCode)
        assertTrue(fileCheckOut.toString().contains("status: valid"))

        val fileSummaryOut = ByteArrayOutputStream()
        val fileSummaryCode = cli.run(
            arrayOf("capsule", "--from", customPath.toString()),
            PrintStream(fileSummaryOut),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, fileSummaryCode)
        assertTrue(fileSummaryOut.toString().contains("capsule: $customPath"))
        assertTrue(fileSummaryOut.toString().contains("valid: true"))
        assertTrue(fileSummaryOut.toString().contains("kaios replay --file $customPath"))

        val fileJsonOut = ByteArrayOutputStream()
        val fileJsonCode = cli.run(
            arrayOf("capsule", "--input", customPath.toString(), "--json"),
            PrintStream(fileJsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val fileJson = Json.parseToJsonElement(fileJsonOut.toString()).jsonObject
        assertEquals(0, fileJsonCode)
        assertEquals(runId, fileJson.getValue("run").jsonObject.getValue("runId").jsonPrimitive.content)

        val replayOut = ByteArrayOutputStream()
        val replayCode = cli.run(
            arrayOf("replay", "--file", customPath.toString()),
            PrintStream(replayOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val replayText = replayOut.toString()
        assertEquals(0, replayCode)
        assertTrue(replayText.contains("KAI CAPSULE REPLAY"))
        assertTrue(replayText.contains("schema: kaios.run-replay/v1"))
        assertTrue(replayText.contains("status: valid"))
        assertTrue(replayText.contains("deterministic: true"))
        assertTrue(replayText.contains("run: $runId"))

        val replayJsonOut = ByteArrayOutputStream()
        val replayJsonCode = cli.run(
            arrayOf("replay", customPath.toString(), "--json"),
            PrintStream(replayJsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val replayJson = Json.parseToJsonElement(replayJsonOut.toString()).jsonObject
        val replayChecks = replayJson.getValue("checks").jsonObject
        assertEquals(0, replayJsonCode)
        assertEquals("kaios.run-replay/v1", replayJson.getValue("schema").jsonPrimitive.content)
        assertEquals(runId, replayJson.getValue("run").jsonObject.getValue("runId").jsonPrimitive.content)
        assertTrue(replayJson.getValue("valid").jsonPrimitive.content == "true")
        assertTrue(replayJson.getValue("deterministic").jsonPrimitive.content == "true")
        assertTrue(replayChecks.getValue("rebuiltTraceMatchesEmbedded").jsonPrimitive.content == "true")

        val replayTamperedPath = workspace.resolve("artifacts/replay-tampered.capsule.json")
        val snapshotTaskMarker = "\"snapshot\": {\n" +
            "        \"runId\": \"$runId\",\n" +
            "        \"workflowName\": \"default\",\n" +
            "        \"task\": \"capture portable agent evidence\""
        assertTrue(Files.readString(customPath).contains(snapshotTaskMarker))
        Files.writeString(
            replayTamperedPath,
            Files.readString(customPath).replace(
                snapshotTaskMarker,
                "\"snapshot\": {\n" +
                    "        \"runId\": \"$runId\",\n" +
                    "        \"workflowName\": \"default\",\n" +
                    "        \"task\": \"tampered replay task\"",
            ),
        )
        val replayTamperedErr = ByteArrayOutputStream()
        val replayTamperedCode = cli.run(
            arrayOf("replay", replayTamperedPath.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(replayTamperedErr),
        )
        assertEquals(2, replayTamperedCode)
        assertTrue(replayTamperedErr.toString().contains("status: invalid"))
        assertTrue(replayTamperedErr.toString().contains("replay.trace must match the trace rebuilt from the embedded snapshot."))

        val tamperedPath = workspace.resolve("artifacts/tampered.capsule.json")
        Files.writeString(
            tamperedPath,
            Files.readString(customPath).replace(
                Regex("\"traceSha256\": \"[a-f0-9]{64}\""),
                "\"traceSha256\": \"0000000000000000000000000000000000000000000000000000000000000000\"",
            ),
        )
        val tamperedErr = ByteArrayOutputStream()
        val tamperedCode = cli.run(
            arrayOf("capsule", "--file", tamperedPath.toString(), "--check"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(tamperedErr),
        )
        assertEquals(2, tamperedCode)
        assertTrue(tamperedErr.toString().contains("provenance.traceSha256 must match the embedded trace."))
    }

    @Test
    fun `diff compares portable capsules by stable runtime signature`() {
        val workspace = Files.createTempDirectory("kaios-cli-diff")
        val cli = cliFor(workspace)

        fun writeCapsule(name: String, vararg task: String): Path {
            val runOut = ByteArrayOutputStream()
            val runCode = cli.run(
                arrayOf("run", *task),
                PrintStream(runOut),
                PrintStream(ByteArrayOutputStream()),
            )
            val runId = Regex("run_id: (\\S+)").find(runOut.toString())?.groupValues?.get(1)
            assertEquals(0, runCode)
            assertTrue(runId != null)

            val capsulePath = workspace.resolve("artifacts/$name.capsule.json")
            val capsuleCode = cli.run(
                arrayOf("capsule", runId, "--out", capsulePath.toString()),
                PrintStream(ByteArrayOutputStream()),
                PrintStream(ByteArrayOutputStream()),
            )
            assertEquals(0, capsuleCode)
            assertTrue(Files.exists(capsulePath))
            return capsulePath
        }

        val baseline = writeCapsule("baseline", "compare", "stable", "task")
        val same = writeCapsule("same", "compare", "stable", "task")
        val changed = writeCapsule("changed", "compare", "changed", "task")
        workspace.resolve("runs").toFile().deleteRecursively()

        val sameOut = ByteArrayOutputStream()
        val sameCode = cli.run(
            arrayOf("diff", baseline.toString(), same.toString()),
            PrintStream(sameOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val sameText = sameOut.toString()
        assertEquals(0, sameCode)
        assertTrue(sameText.contains("KAI CAPSULE DIFF"))
        assertTrue(sameText.contains("schema: kaios.run-diff/v1"))
        assertTrue(sameText.contains("status: same"))
        assertTrue(sameText.contains("same: true"))
        assertTrue(sameText.contains("differences: none"))

        val sameJsonOut = ByteArrayOutputStream()
        val sameJsonCode = cli.run(
            arrayOf("diff", "--left", baseline.toString(), "--right", same.toString(), "--json"),
            PrintStream(sameJsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val sameJson = Json.parseToJsonElement(sameJsonOut.toString()).jsonObject
        assertEquals(0, sameJsonCode)
        assertEquals("kaios.run-diff/v1", sameJson.getValue("schema").jsonPrimitive.content)
        assertEquals("same", sameJson.getValue("result").jsonPrimitive.content)
        assertTrue(sameJson.getValue("same").jsonPrimitive.content == "true")
        assertEquals(
            sameJson.getValue("left").jsonObject.getValue("stableSha256").jsonPrimitive.content,
            sameJson.getValue("right").jsonObject.getValue("stableSha256").jsonPrimitive.content,
        )

        val changedOut = ByteArrayOutputStream()
        val changedCode = cli.run(
            arrayOf("diff", baseline.toString(), changed.toString()),
            PrintStream(changedOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val changedText = changedOut.toString()
        assertEquals(0, changedCode)
        assertTrue(changedText.contains("status: different"))
        assertTrue(changedText.contains("same: false"))
        assertTrue(changedText.contains("task:"))
        assertTrue(changedText.contains("finalOutputSha256:"))

        val checkErr = ByteArrayOutputStream()
        val checkCode = cli.run(
            arrayOf("diff", baseline.toString(), changed.toString(), "--check"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(checkErr),
        )
        assertEquals(1, checkCode)
        assertTrue(checkErr.toString().contains("status: different"))
    }

    @Test
    fun `evidence packages validates replays and gates baseline diffs`() {
        val workspace = Files.createTempDirectory("kaios-cli-evidence")
        val cli = cliFor(workspace)

        fun runTask(vararg task: String): String {
            val runOut = ByteArrayOutputStream()
            val runCode = cli.run(
                arrayOf("run", *task),
                PrintStream(runOut),
                PrintStream(ByteArrayOutputStream()),
            )
            val runId = Regex("run_id: (\\S+)").find(runOut.toString())?.groupValues?.get(1)
            assertEquals(0, runCode)
            assertTrue(runId != null)
            return runId
        }

        val runId = runTask("package", "one", "command", "evidence")
        val evidencePath = workspace.resolve("artifacts/evidence.capsule.json")
        val evidenceOut = ByteArrayOutputStream()
        val evidenceCode = cli.run(
            arrayOf("evidence", runId, "--out", evidencePath.toString()),
            PrintStream(evidenceOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val evidenceText = evidenceOut.toString()
        val capsuleJson = Json.parseToJsonElement(Files.readString(evidencePath)).jsonObject

        assertEquals(0, evidenceCode)
        assertTrue(Files.exists(evidencePath))
        assertTrue(evidenceText.contains("KAI EVIDENCE"))
        assertTrue(evidenceText.contains("schema: kaios.evidence/v1"))
        assertTrue(evidenceText.contains("run: $runId"))
        assertTrue(evidenceText.contains("status: valid"))
        assertTrue(evidenceText.contains("capsule_status: valid"))
        assertTrue(evidenceText.contains("replay_status: valid"))
        assertTrue(evidenceText.contains("diff_status: skipped"))
        assertTrue(evidenceText.contains("kaios replay --file"))
        assertEquals("kaios.run-capsule/v1", capsuleJson.getValue("schema").jsonPrimitive.content)
        assertEquals(runId, capsuleJson.getValue("run").jsonObject.getValue("runId").jsonPrimitive.content)

        val protectedErr = ByteArrayOutputStream()
        val protectedCode = cli.run(
            arrayOf("evidence", runId, "--out", evidencePath.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(protectedErr),
        )
        assertEquals(1, protectedCode)
        assertTrue(protectedErr.toString().contains("already exists"))

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(
            arrayOf("evidence", "latest", "--out", evidencePath.toString(), "--json", "--force"),
            PrintStream(jsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val json = Json.parseToJsonElement(jsonOut.toString()).jsonObject
        val jsonCapsule = json.getValue("capsule").jsonObject
        val jsonReplay = json.getValue("replay").jsonObject
        val jsonDiff = json.getValue("diff").jsonObject
        assertEquals(0, jsonCode)
        assertEquals("kaios.evidence/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("valid", json.getValue("status").jsonPrimitive.content)
        assertTrue(json.getValue("valid").jsonPrimitive.content == "true")
        assertEquals("valid", jsonCapsule.getValue("status").jsonPrimitive.content)
        assertEquals("valid", jsonReplay.getValue("status").jsonPrimitive.content)
        assertEquals("skipped", jsonDiff.getValue("status").jsonPrimitive.content)

        val baselineRunId = runTask("compare", "stable", "task")
        val baselinePath = workspace.resolve("artifacts/baseline.capsule.json")
        val baselineCode = cli.run(
            arrayOf("evidence", baselineRunId, "--out", baselinePath.toString(), "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(0, baselineCode)

        val samePathErr = ByteArrayOutputStream()
        val samePathCode = cli.run(
            arrayOf(
                "evidence",
                baselineRunId,
                "--out",
                baselinePath.toString(),
                "--baseline",
                baselinePath.toString(),
                "--force",
            ),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(samePathErr),
        )
        assertEquals(1, samePathCode)
        assertTrue(samePathErr.toString().contains("must be different files"))

        val changedRunId = runTask("compare", "changed", "task")
        val changedPath = workspace.resolve("artifacts/changed.capsule.json")
        val changedErr = ByteArrayOutputStream()
        val changedCode = cli.run(
            arrayOf(
                "evidence",
                changedRunId,
                "--out",
                changedPath.toString(),
                "--baseline",
                baselinePath.toString(),
                "--check",
            ),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(changedErr),
        )
        val changedText = changedErr.toString()
        assertEquals(1, changedCode)
        assertTrue(changedText.contains("status: different"))
        assertTrue(changedText.contains("diff_status: different"))
        assertTrue(changedText.contains("differences:"))
        assertTrue(Files.exists(changedPath))
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
    fun `run trace out writes process trace json and protects existing files`() {
        val workspace = Files.createTempDirectory("kaios-cli-run-trace-out")
        val cli = cliFor(workspace)
        val trace = workspace.resolve("artifacts/trace.json")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("run", "--trace-out", trace.toString(), "record", "a", "trace"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val traceJson = Json.parseToJsonElement(Files.readString(trace)).jsonObject

        assertEquals(0, code)
        assertTrue(text.contains("trace: $trace"))
        assertEquals("kaios.process-trace/v1", traceJson.getValue("schema").jsonPrimitive.content)
        assertEquals(3, traceJson.getValue("metrics").jsonObject.getValue("processCount").jsonPrimitive.int)

        val err = ByteArrayOutputStream()
        val secondCode = cli.run(
            arrayOf("run", "--trace-out", trace.toString(), "record", "again"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, secondCode)
        assertTrue(err.toString().contains("already exists"))
        assertTrue(err.toString().contains("Use --force"))

        val forcedCode = cli.run(
            arrayOf("run", "--trace-out", trace.toString(), "--force", "record", "again"),
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
    fun `setup creates a validated research workflow and is idempotent`() {
        val workspace = Files.createTempDirectory("kaios-cli-setup")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("setup"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val configPath = workspace.resolve("kaios.json")
        val configText = Files.readString(configPath)
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("KAI OS setup"))
        assertTrue(text.contains("schema: kaios.setup/v1"))
        assertTrue(text.contains("requested_template: research"))
        assertTrue(text.contains("config_action: created"))
        assertTrue(text.contains("validation: valid"))
        assertTrue(text.contains("workflow: research"))
        assertTrue(configText.contains("\"researcher\""))
        assertTrue(configText.contains("\"synthesizer\""))

        val secondOut = ByteArrayOutputStream()
        val secondCode = cli.run(arrayOf("setup"), PrintStream(secondOut), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, secondCode)
        assertTrue(secondOut.toString().contains("config_action: existing"))
        assertEquals(configText, Files.readString(configPath))
    }

    @Test
    fun `setup ci json writes an agent gate and reports actions`() {
        val workspace = Files.createTempDirectory("kaios-cli-setup-ci-json")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("setup", "--template", "code-review", "--ci", "--json"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val config = json.getValue("config").jsonObject
        val ci = json.getValue("ci").jsonObject
        val validation = json.getValue("validation").jsonObject
        val doctor = json.getValue("doctor").jsonObject
        val workflowPath = workspace.resolve(".github").resolve("workflows").resolve("kaios.yml")
        val workflowText = Files.readString(workflowPath)

        assertEquals(0, code)
        assertEquals("kaios.setup/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("0.1.57", json.getValue("version").jsonPrimitive.content)
        assertEquals("code-review", json.getValue("requestedTemplate").jsonPrimitive.content)
        assertEquals("created", config.getValue("action").jsonPrimitive.content)
        assertEquals("created", ci.getValue("action").jsonPrimitive.content)
        assertTrue(ci.getValue("path").jsonPrimitive.content.endsWith(".github/workflows/kaios.yml"))
        assertEquals("code-review", validation.getValue("workflowName").jsonPrimitive.content)
        assertTrue(validation.getValue("valid").jsonPrimitive.content == "true")
        assertEquals("ready", doctor.getValue("summary").jsonObject.getValue("status").jsonPrimitive.content)
        assertTrue(workflowText.contains("KAIOS_VERSION: \"0.1.57\""))
        assertTrue(workflowText.contains("kaios verify --config 'kaios.json' --evidence --force"))
        assertTrue(workflowText.contains("uses: actions/upload-artifact@v4"))
        assertTrue(!workflowText.contains("kaios config validate --config 'kaios.json' --json"))
    }

    @Test
    fun `verify fails clearly before setup when config is missing`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-missing")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("verify"), PrintStream(out), PrintStream(err))
        val text = out.toString()

        assertEquals(2, code)
        assertEquals("", err.toString())
        assertTrue(text.contains("KAI OS verify"))
        assertTrue(text.contains("schema: kaios.verify/v1"))
        assertTrue(text.contains("status: failed"))
        assertTrue(text.contains("doctor: ready"))
        assertTrue(text.contains("config: invalid"))
        assertTrue(text.contains("run: skipped"))
        assertTrue(text.contains("trace: skipped"))
        assertTrue(text.contains("Config file '${workspace.resolve("kaios.json")}' was not found."))
        assertTrue(text.contains("kaios setup --ci"))
    }

    @Test
    fun `verify runs configured workflow and validates trace`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify")
        val cli = cliFor(workspace)
        val setupOut = ByteArrayOutputStream()
        val setupCode = cli.run(arrayOf("setup", "--ci"), PrintStream(setupOut), PrintStream(ByteArrayOutputStream()))
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("verify"), PrintStream(out), PrintStream(err))
        val text = out.toString()
        val runId = Regex("run: (\\S+) success=true").find(text)?.groupValues?.get(1)

        assertEquals(0, setupCode)
        assertEquals(0, code)
        assertEquals("", err.toString())
        assertTrue(text.contains("KAI OS verify"))
        assertTrue(text.contains("schema: kaios.verify/v1"))
        assertTrue(text.contains("status: ready"))
        assertTrue(text.contains("doctor: ready"))
        assertTrue(text.contains("config: valid"))
        assertTrue(text.contains("workflow: research"))
        assertTrue(text.contains("trace: valid"))
        assertTrue(text.contains("processes: 3"))
        assertTrue(runId != null)

        val psOut = ByteArrayOutputStream()
        val psCode = cli.run(arrayOf("ps", "latest"), PrintStream(psOut), PrintStream(ByteArrayOutputStream()))
        val psText = psOut.toString()

        assertEquals(0, psCode)
        assertTrue(psText.contains("RUN $runId"))
        assertTrue(psText.contains("workflow=research"))
        assertTrue(psText.contains("researcher"))
    }

    @Test
    fun `verify can write and report an evidence artifact`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-evidence")
        val cli = cliFor(workspace)
        val setupCode = cli.run(arrayOf("setup"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
        val evidencePath = workspace.resolve("artifacts/verify.capsule.json")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("verify", "--evidence-out", evidencePath.toString(), "--force"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val capsuleJson = Json.parseToJsonElement(Files.readString(evidencePath)).jsonObject

        assertEquals(0, setupCode)
        assertEquals(0, code)
        assertTrue(text.contains("status: ready"))
        assertTrue(text.contains("evidence: valid (kaios.evidence/v1)"))
        assertTrue(text.contains("capsule: $evidencePath"))
        assertTrue(text.contains("replay: valid"))
        assertTrue(text.contains("diff: skipped"))
        assertEquals("kaios.run-capsule/v1", capsuleJson.getValue("schema").jsonPrimitive.content)

        val protectedOut = ByteArrayOutputStream()
        val protectedCode = cli.run(
            arrayOf("verify", "--evidence-out", evidencePath.toString()),
            PrintStream(protectedOut),
            PrintStream(ByteArrayOutputStream()),
        )
        assertEquals(2, protectedCode)
        assertTrue(protectedOut.toString().contains("Output '$evidencePath' already exists. Use --force to overwrite it."))

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(
            arrayOf("verify", "--evidence-out", evidencePath.toString(), "--json", "--force"),
            PrintStream(jsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val json = Json.parseToJsonElement(jsonOut.toString()).jsonObject
        val evidence = json.getValue("evidence").jsonObject

        assertEquals(0, jsonCode)
        assertEquals("kaios.verify/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("ready", json.getValue("status").jsonPrimitive.content)
        assertEquals("kaios.evidence/v1", evidence.getValue("schema").jsonPrimitive.content)
        assertEquals("valid", evidence.getValue("status").jsonPrimitive.content)
        assertEquals("skipped", evidence.getValue("diff").jsonObject.getValue("status").jsonPrimitive.content)

        val defaultPath = workspace.resolve("artifacts/kaios-run.capsule.json")
        val defaultJsonOut = ByteArrayOutputStream()
        val defaultJsonCode = cli.run(
            arrayOf("verify", "--evidence", "--json", "--force"),
            PrintStream(defaultJsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val defaultJson = Json.parseToJsonElement(defaultJsonOut.toString()).jsonObject
        val defaultEvidence = defaultJson.getValue("evidence").jsonObject
        val defaultCapsuleJson = Json.parseToJsonElement(Files.readString(defaultPath)).jsonObject

        assertEquals(0, defaultJsonCode)
        assertTrue(Files.exists(defaultPath))
        assertEquals("kaios.verify/v1", defaultJson.getValue("schema").jsonPrimitive.content)
        assertEquals("kaios.evidence/v1", defaultEvidence.getValue("schema").jsonPrimitive.content)
        assertEquals(defaultPath.toString(), defaultEvidence.getValue("capsulePath").jsonPrimitive.content)
        assertEquals("kaios.run-capsule/v1", defaultCapsuleJson.getValue("schema").jsonPrimitive.content)
    }

    @Test
    fun `verify evidence check fails when baseline behavior differs`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-evidence-diff")
        val cli = cliFor(workspace)
        val setupCode = cli.run(arrayOf("setup"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
        val baselineRunOut = ByteArrayOutputStream()
        val baselineRunCode = cli.run(
            arrayOf("run", "different", "baseline", "task"),
            PrintStream(baselineRunOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val baselineRunId = Regex("run_id: (\\S+)").find(baselineRunOut.toString())?.groupValues?.get(1)
        val baselinePath = workspace.resolve("artifacts/baseline.capsule.json")
        val baselineCode = cli.run(
            arrayOf("evidence", baselineRunId ?: "", "--out", baselinePath.toString(), "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        val currentPath = workspace.resolve("artifacts/current.capsule.json")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf(
                "verify",
                "--evidence-out",
                currentPath.toString(),
                "--baseline",
                baselinePath.toString(),
                "--check",
                "--force",
            ),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()

        assertEquals(0, setupCode)
        assertEquals(0, baselineRunCode)
        assertTrue(baselineRunId != null)
        assertEquals(0, baselineCode)
        assertEquals(1, code)
        assertTrue(text.contains("status: ready"))
        assertTrue(text.contains("evidence: different (kaios.evidence/v1)"))
        assertTrue(text.contains("diff: different"))
        assertTrue(Files.exists(currentPath))
    }

    @Test
    fun `verify json reports run and trace contract`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-json")
        val cli = cliFor(workspace)
        val setupCode = cli.run(
            arrayOf("setup", "--template", "code-review"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("verify", "--json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val config = json.getValue("config").jsonObject
        val run = json.getValue("run").jsonObject
        val trace = json.getValue("trace").jsonObject

        assertEquals(0, setupCode)
        assertEquals(0, code)
        assertEquals("kaios.verify/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("0.1.57", json.getValue("version").jsonPrimitive.content)
        assertEquals("ready", json.getValue("status").jsonPrimitive.content)
        assertEquals("code-review", config.getValue("workflowName").jsonPrimitive.content)
        assertTrue(config.getValue("valid").jsonPrimitive.content == "true")
        assertEquals("code-review", run.getValue("workflowName").jsonPrimitive.content)
        assertTrue(run.getValue("success").jsonPrimitive.content == "true")
        assertEquals("kaios.process-trace/v1", trace.getValue("schema").jsonPrimitive.content)
        assertTrue(trace.getValue("valid").jsonPrimitive.content == "true")
        assertEquals(3, trace.getValue("processCount").jsonPrimitive.int)
    }

    @Test
    fun `setup and verify warn instead of failing on optional runtime env errors`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-runtime-warnings")
        val cli = KaiosCli(
            FileRunSnapshotStore(workspace.resolve("runs")),
            workspace.resolve("reports"),
            artifactRoot = workspace.resolve("artifacts"),
            snapshotRoot = workspace.resolve("runs"),
            workingDir = workspace,
            env = { key ->
                mapOf(
                    "KAIOS_MODEL_PROVIDER" to "openai",
                    "OPENAI_API_KEY" to "secret-key",
                    "KAIOS_MEMORY_STORE" to "bad-store",
                )[key]
            },
        )
        val setupOut = ByteArrayOutputStream()

        val setupCode = cli.run(arrayOf("setup"), PrintStream(setupOut), PrintStream(ByteArrayOutputStream()))
        val setupText = setupOut.toString()

        assertEquals(0, setupCode)
        assertTrue(setupText.contains("doctor: ready with 2 warning(s)"))
        assertTrue(setupText.contains("warnings:"))
        assertTrue(setupText.contains("model provider: OPENAI_MODEL is required"))
        assertTrue(setupText.contains("memory store: Unsupported KAIOS_MEMORY_STORE 'bad-store'"))
        assertTrue(!setupText.contains("secret-key"))

        val verifyOut = ByteArrayOutputStream()
        val verifyCode = cli.run(arrayOf("verify"), PrintStream(verifyOut), PrintStream(ByteArrayOutputStream()))
        val verifyText = verifyOut.toString()

        assertEquals(0, verifyCode)
        assertTrue(verifyText.contains("status: ready"))
        assertTrue(verifyText.contains("doctor: ready with 2 warning(s)"))
        assertTrue(verifyText.contains("trace: valid"))
        assertTrue(verifyText.contains("model provider: OPENAI_MODEL is required"))
        assertTrue(verifyText.contains("memory store: Unsupported KAIOS_MEMORY_STORE 'bad-store'"))
        assertTrue(!verifyText.contains("secret-key"))

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(arrayOf("verify", "--json"), PrintStream(jsonOut), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(jsonOut.toString()).jsonObject
        val summary = json.getValue("doctor").jsonObject.getValue("summary").jsonObject

        assertEquals(0, jsonCode)
        assertEquals("ready", json.getValue("status").jsonPrimitive.content)
        assertEquals(0, summary.getValue("failed").jsonPrimitive.int)
        assertEquals(2, summary.getValue("warnings").jsonPrimitive.int)
    }

    @Test
    fun `verify config path is not blocked by an invalid default config`() {
        val workspace = Files.createTempDirectory("kaios-cli-verify-custom-config")
        Files.writeString(workspace.resolve("kaios.json"), """{"name":"","agents":[]}""")
        val cli = cliFor(workspace)
        val initCode = cli.run(
            arrayOf("init", "--template", "research", "--config", "workflows/research.json"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("verify", "--config", "workflows/research.json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, initCode)
        assertEquals(0, code)
        assertTrue(text.contains("status: ready"))
        assertTrue(text.contains("doctor: ready"))
        assertTrue(text.contains("config: valid (${workspace.resolve("workflows/research.json")})"))
        assertTrue(text.contains("workflow: research"))
        assertTrue(!text.contains("Config field 'name' cannot be blank."))
    }

    @Test
    fun `setup custom config path is not blocked by an invalid default config`() {
        val workspace = Files.createTempDirectory("kaios-cli-setup-custom-config")
        Files.writeString(workspace.resolve("kaios.json"), """{"name":"","agents":[]}""")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("setup", "--config", "workflows/research.json", "--ci"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val text = out.toString()
        val workflowText = Files.readString(workspace.resolve(".github").resolve("workflows").resolve("kaios.yml"))

        assertEquals(0, code)
        assertTrue(text.contains("doctor: ready"))
        assertTrue(text.contains("config_action: created"))
        assertTrue(text.contains("validation: valid"))
        assertTrue(text.contains("workflow: research"))
        assertTrue(text.contains("kaios verify --config workflows/research.json"))
        assertTrue(workflowText.contains("kaios verify --config 'workflows/research.json' --evidence --force"))
        assertTrue(Files.readString(workspace.resolve("kaios.json")).contains("\"name\":"))
    }

    @Test
    fun `setup keeps invalid existing config until force is passed`() {
        val workspace = Files.createTempDirectory("kaios-cli-setup-invalid")
        val configPath = workspace.resolve("kaios.json")
        Files.writeString(configPath, """{"name":"","agents":[]}""")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("setup"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(1, code)
        assertTrue(text.contains("config_action: existing"))
        assertTrue(text.contains("validation: invalid"))
        assertTrue(text.contains("Config field 'name' cannot be blank."))
        assertTrue(Files.readString(configPath).contains("\"name\":"))

        val forceOut = ByteArrayOutputStream()
        val forceCode = cli.run(arrayOf("setup", "--force"), PrintStream(forceOut), PrintStream(ByteArrayOutputStream()))

        assertEquals(0, forceCode)
        assertTrue(forceOut.toString().contains("config_action: overwritten"))
        assertTrue(forceOut.toString().contains("validation: valid"))
        assertTrue(Files.readString(configPath).contains("\"researcher\""))
    }

    @Test
    fun `init ci writes a GitHub Actions agent gate`() {
        val workspace = Files.createTempDirectory("kaios-cli-init-ci")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("init", "--template", "research", "--ci"),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )
        val configPath = workspace.resolve("kaios.json")
        val workflowPath = workspace.resolve(".github").resolve("workflows").resolve("kaios.yml")
        val workflowText = Files.readString(workflowPath)
        val outputText = out.toString()

        assertEquals(0, code)
        assertTrue(Files.exists(configPath))
        assertTrue(outputText.contains("created_ci: $workflowPath"))
        assertTrue(outputText.contains("kaios config validate --config kaios.json --json"))
        assertTrue(outputText.contains("kaios verify --config kaios.json --evidence --force"))
        assertTrue(workflowText.contains("name: KAI OS Agent Gate"))
        assertTrue(workflowText.contains("KAIOS_VERSION: \"0.1.57\""))
        assertTrue(workflowText.contains("KAIOS_MODEL_PROVIDER: mock"))
        assertTrue(workflowText.contains("kaios verify --config 'kaios.json' --evidence --force"))
        assertTrue(workflowText.contains("name: kaios-evidence"))
        assertTrue(!workflowText.contains("kaios doctor --json"))
        assertTrue(!workflowText.contains("kaios run --config 'kaios.json' --trace-out .kaios/artifacts/ci-trace.json --force"))
    }

    @Test
    fun `init ci refuses to overwrite an existing workflow without force`() {
        val workspace = Files.createTempDirectory("kaios-cli-init-ci-existing")
        val workflowPath = workspace.resolve(".github").resolve("workflows").resolve("kaios.yml")
        Files.createDirectories(workflowPath.parent)
        Files.writeString(workflowPath, "name: Existing\n")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("init", "--ci"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, code)
        assertTrue(!Files.exists(workspace.resolve("kaios.json")))
        assertEquals("name: Existing\n", Files.readString(workflowPath))
        assertTrue(err.toString().contains("CI workflow '$workflowPath' already exists. Use --force to overwrite it."))
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
    fun `config show and validate explain how to create a missing config`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-missing")
        val cli = cliFor(workspace)

        listOf(
            arrayOf("config", "show"),
            arrayOf("config", "validate"),
        ).forEach { args ->
            val err = ByteArrayOutputStream()
            val code = cli.run(args, PrintStream(ByteArrayOutputStream()), PrintStream(err))
            val text = err.toString()

            assertEquals(1, code)
            assertTrue(text.contains("Config file '${workspace.resolve("kaios.json")}' was not found."))
            assertTrue(text.contains("Run 'kaios init --template default' to create a local workflow config."))
            assertTrue(text.contains("Run 'kaios config templates' to list available templates."))
            assertTrue(text.contains("Use '--config path/to/kaios.json' to inspect another config file."))
        }
    }

    @Test
    fun `config subcommands support direct and named help`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-help")
        val cli = cliFor(workspace)
        val cases = listOf(
            arrayOf("config", "templates", "--help") to "Usage: kaios config templates",
            arrayOf("config", "validate", "--help") to "Usage: kaios config validate",
            arrayOf("config", "show", "--help") to "Usage: kaios config show",
            arrayOf("help", "config", "show") to "Usage: kaios config show",
            arrayOf("help", "config", "templates") to "Usage: kaios config templates",
        )

        cases.forEach { (args, usage) ->
            val out = ByteArrayOutputStream()
            val code = cli.run(args, PrintStream(out), PrintStream(ByteArrayOutputStream()))
            val text = out.toString()

            assertEquals(0, code)
            assertTrue(text.contains(usage), args.joinToString(" "))
            assertTrue(text.contains("Examples:"), args.joinToString(" "))
            assertTrue(!text.contains("Unknown"), args.joinToString(" "))
        }
    }

    @Test
    fun `help command suggests close config subcommand names`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-help-suggest")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("help", "config", "shwo"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown command 'config shwo'"))
        assertTrue(text.contains("Did you mean 'kaios help config show'?"))
        assertTrue(text.contains("Usage: kaios help <command>"))
    }

    @Test
    fun `config templates rejects unknown arguments with help pointer`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-templates-unknown")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("config", "templates", "--bad"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown config templates option '--bad'"))
        assertTrue(text.contains("Usage: kaios config templates"))
        assertTrue(text.contains("Run 'kaios help config templates' for examples."))
    }

    @Test
    fun `config command suggests close subcommand names`() {
        val workspace = Files.createTempDirectory("kaios-cli-config-suggest")
        val cli = cliFor(workspace)
        val err = ByteArrayOutputStream()

        val code = cli.run(arrayOf("config", "shwo"), PrintStream(ByteArrayOutputStream()), PrintStream(err))
        val text = err.toString()

        assertEquals(1, code)
        assertTrue(text.contains("Unknown config command 'shwo'"))
        assertTrue(text.contains("Did you mean 'kaios config show'?"))
        assertTrue(text.contains("Run 'kaios help config' for examples."))
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

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(
            arrayOf("config", "validate", "--config", config.toString(), "--json"),
            PrintStream(jsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val json = Json.parseToJsonElement(jsonOut.toString()).jsonObject

        assertEquals(0, jsonCode)
        assertEquals("kaios.config-validation/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals(true, json.getValue("valid").jsonPrimitive.content.toBoolean())
        assertEquals("http-research", json.getValue("workflowName").jsonPrimitive.content)
        assertEquals(1, json.getValue("agentCount").jsonPrimitive.int)
        assertEquals("researcher", json.getValue("agentIds").jsonArray.single().jsonPrimitive.content)
        assertEquals(0, json.getValue("errors").jsonArray.size)
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

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(
            arrayOf("config", "validate", "--config", config.toString(), "--format", "json"),
            PrintStream(jsonOut),
            PrintStream(ByteArrayOutputStream()),
        )
        val jsonText = jsonOut.toString()
        val json = Json.parseToJsonElement(jsonText).jsonObject

        assertEquals(1, jsonCode)
        assertEquals("kaios.config-validation/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals(false, json.getValue("valid").jsonPrimitive.content.toBoolean())
        assertEquals(0, json.getValue("agentCount").jsonPrimitive.int)
        assertTrue(jsonText.contains("retries must be between 0 and 10"))
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
        assertTrue(text.contains("kaios demo"))
        assertTrue(text.contains("kaios setup --ci"))
        assertTrue(text.contains("kaios analyze . --out artifacts/analysis.md --force"))
        assertTrue(!text.contains("kaios run --index ."))
    }

    @Test
    fun `doctor json reports stable machine readable diagnostics`() {
        val root = Files.createTempDirectory("kaios-cli-doctor-json-runs")
        val reportRoot = Files.createTempDirectory("kaios-cli-doctor-json-reports")
        val artifactRoot = Files.createTempDirectory("kaios-cli-doctor-json-artifacts")
        val cli = KaiosCli(FileRunSnapshotStore(root), reportRoot, artifactRoot = artifactRoot, snapshotRoot = root)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("doctor", "--json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val summary = json.getValue("summary").jsonObject
        val checks = json.getValue("checks").jsonArray
        val next = json.getValue("next").jsonArray

        assertEquals(0, code)
        assertEquals("kaios.doctor/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("0.1.57", json.getValue("version").jsonPrimitive.content)
        assertEquals("ready", summary.getValue("status").jsonPrimitive.content)
        assertEquals(0, summary.getValue("failed").jsonPrimitive.int)
        assertTrue(checks.any { check ->
            val item = check.jsonObject
            item.getValue("name").jsonPrimitive.content == "model provider" &&
                item.getValue("status").jsonPrimitive.content == "OK" &&
                item.getValue("detail").jsonPrimitive.content.contains("mock")
        })
        assertTrue(next.any { command -> command.jsonPrimitive.content == "kaios demo" })
        assertTrue(next.any { command -> command.jsonPrimitive.content == "kaios setup --ci" })
        assertTrue(next.any { command -> command.jsonPrimitive.content == "kaios analyze . --out artifacts/analysis.md --force" })
    }

    @Test
    fun `doctor recommends verify when project config exists`() {
        val workspace = Files.createTempDirectory("kaios-cli-doctor-config-next")
        val cli = cliFor(workspace)
        val setupCode = cli.run(arrayOf("setup"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("doctor", "--json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val next = json.getValue("next").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(0, setupCode)
        assertEquals(0, code)
        assertTrue(next.contains("kaios demo"))
        assertTrue(next.contains("kaios verify --config kaios.json --evidence --force"))
        assertTrue(next.contains("kaios analyze . --out artifacts/analysis.md --force"))
        assertTrue(!next.any { it.contains("kaios run --index .") })
    }

    @Test
    fun `bug report prints safe markdown without a saved run`() {
        val workspace = Files.createTempDirectory("kaios-cli-bug-report-empty")
        val cli = cliFor(workspace)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("bug-report"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("# KAI OS Bug Report"))
        assertTrue(text.contains("schema: `kaios.bug-report/v1`"))
        assertTrue(text.contains("version: `0.1.57`"))
        assertTrue(text.contains("## What Happened"))
        assertTrue(text.contains("## Doctor"))
        assertTrue(text.contains("No saved run snapshot was found."))
        assertTrue(text.contains("Config file '${workspace.resolve("kaios.json")}' was not found."))
        assertTrue(text.contains("- `kaios setup --ci`"))
        assertTrue(text.contains("- `kaios demo`"))
        assertTrue(!text.contains("kaios init --template research --ci"))
        assertTrue(!text.contains("kaios run --index ."))
        assertTrue(!text.contains("secret-key"))
    }

    @Test
    fun `bug report json includes latest run and trace status`() {
        val workspace = Files.createTempDirectory("kaios-cli-bug-report-json")
        val cli = cliFor(workspace)
        val demoOut = ByteArrayOutputStream()
        val demoCode = cli.run(arrayOf("demo"), PrintStream(demoOut), PrintStream(ByteArrayOutputStream()))
        val runId = Regex("run_id: (\\S+)").find(demoOut.toString())?.groupValues?.get(1)
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("bug-report", "--json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val latestRun = json.getValue("latestRun").jsonObject
        val trace = json.getValue("trace").jsonObject
        val config = json.getValue("config").jsonObject

        assertEquals(0, demoCode)
        assertEquals(0, code)
        assertEquals("kaios.bug-report/v1", json.getValue("schema").jsonPrimitive.content)
        assertEquals("0.1.57", json.getValue("version").jsonPrimitive.content)
        assertEquals(runId, latestRun.getValue("runId").jsonPrimitive.content)
        assertEquals("default", latestRun.getValue("workflowName").jsonPrimitive.content)
        assertEquals(3, latestRun.getValue("processCount").jsonPrimitive.int)
        assertEquals(runId, trace.getValue("runId").jsonPrimitive.content)
        assertTrue(trace.getValue("valid").jsonPrimitive.content == "true")
        assertEquals(3, trace.getValue("processCount").jsonPrimitive.int)
        assertEquals("kaios.config-validation/v1", config.getValue("schema").jsonPrimitive.content)
        assertTrue(config.getValue("valid").jsonPrimitive.content == "false")
        val next = json.getValue("next").jsonArray.map { it.jsonPrimitive.content }
        assertTrue(next.contains("kaios setup --ci"))
        assertTrue(next.contains("kaios ps latest"))
        assertTrue(next.contains("kaios trace latest --check"))
        assertTrue(next.contains("kaios evidence latest"))
        assertTrue(next.contains("kaios inspect latest"))
        assertTrue(!next.any { it.contains("kaios init --template research --ci") })
        assertTrue(!next.any { it.contains("kaios run --index .") })
    }

    @Test
    fun `bug report recommends verify when project config is valid`() {
        val workspace = Files.createTempDirectory("kaios-cli-bug-report-config-next")
        val cli = cliFor(workspace)
        val setupCode = cli.run(arrayOf("setup"), PrintStream(ByteArrayOutputStream()), PrintStream(ByteArrayOutputStream()))
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("bug-report", "--json"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val json = Json.parseToJsonElement(out.toString()).jsonObject
        val config = json.getValue("config").jsonObject
        val next = json.getValue("next").jsonArray.map { it.jsonPrimitive.content }

        assertEquals(0, setupCode)
        assertEquals(0, code)
        assertTrue(config.getValue("valid").jsonPrimitive.content == "true")
        assertTrue(next.contains("kaios verify --config kaios.json --evidence --force"))
        assertTrue(next.contains("kaios demo"))
        assertTrue(next.contains("kaios doctor --json"))
        assertTrue(!next.contains("kaios setup --ci"))
        assertTrue(!next.any { it.contains("kaios run --index .") })
    }

    @Test
    fun `bug report output file is protected unless forced`() {
        val workspace = Files.createTempDirectory("kaios-cli-bug-report-out")
        val cli = cliFor(workspace)
        val report = workspace.resolve("artifacts").resolve("kaios-bug-report.md")
        val out = ByteArrayOutputStream()

        val code = cli.run(
            arrayOf("bug-report", "--out", report.toString()),
            PrintStream(out),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, code)
        assertTrue(out.toString().contains("bug_report: $report"))
        assertTrue(Files.readString(report).contains("# KAI OS Bug Report"))

        val err = ByteArrayOutputStream()
        val protectedCode = cli.run(
            arrayOf("bug-report", "--out", report.toString()),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(err),
        )

        assertEquals(1, protectedCode)
        assertTrue(err.toString().contains("already exists"))

        val forcedCode = cli.run(
            arrayOf("bug-report", "--out", report.toString(), "--force"),
            PrintStream(ByteArrayOutputStream()),
            PrintStream(ByteArrayOutputStream()),
        )

        assertEquals(0, forcedCode)
    }

    @Test
    fun `bug report still succeeds when doctor finds provider errors`() {
        val root = Files.createTempDirectory("kaios-cli-bug-report-bad-provider-runs")
        val reportRoot = Files.createTempDirectory("kaios-cli-bug-report-bad-provider-reports")
        val cli = KaiosCli(
            FileRunSnapshotStore(root),
            reportRoot,
            artifactRoot = Files.createTempDirectory("kaios-cli-bug-report-bad-provider-artifacts"),
            snapshotRoot = root,
            env = { key ->
                mapOf(
                    "KAIOS_MODEL_PROVIDER" to "openai",
                    "OPENAI_API_KEY" to "secret-key",
                )[key]
            },
        )
        val out = ByteArrayOutputStream()

        val code = cli.run(arrayOf("bug-report"), PrintStream(out), PrintStream(ByteArrayOutputStream()))
        val text = out.toString()

        assertEquals(0, code)
        assertTrue(text.contains("[FAIL] model provider"))
        assertTrue(text.contains("OPENAI_MODEL is required"))
        assertTrue(!text.contains("secret-key"))
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

        val jsonOut = ByteArrayOutputStream()
        val jsonCode = cli.run(arrayOf("doctor", "--format", "json"), PrintStream(jsonOut), PrintStream(ByteArrayOutputStream()))
        val jsonText = jsonOut.toString()
        val json = Json.parseToJsonElement(jsonText).jsonObject
        val summary = json.getValue("summary").jsonObject

        assertEquals(2, jsonCode)
        assertEquals("failed", summary.getValue("status").jsonPrimitive.content)
        assertEquals(1, summary.getValue("failed").jsonPrimitive.int)
        assertTrue(jsonText.contains("OPENAI_MODEL is required"))
        assertTrue(!jsonText.contains("secret-key"))
    }
}

private fun cliFor(workspace: Path): KaiosCli {
    val runs = workspace.resolve("runs")
    return KaiosCli(
        FileRunSnapshotStore(runs),
        workspace.resolve("reports"),
        artifactRoot = workspace.resolve("artifacts"),
        capsuleRoot = workspace.resolve("capsules"),
        snapshotRoot = runs,
        workingDir = workspace,
    )
}
