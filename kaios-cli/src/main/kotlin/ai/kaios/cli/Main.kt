package ai.kaios.cli

import ai.kaios.AgentRuntime
import ai.kaios.FileRunSnapshotStore
import ai.kaios.MemoryStore
import ai.kaios.ModelProvider
import ai.kaios.MockModelProvider
import ai.kaios.OllamaConfig
import ai.kaios.OllamaModelProvider
import ai.kaios.OpenAiCompatibleConfig
import ai.kaios.OpenAiCompatibleModelProvider
import ai.kaios.RunId
import ai.kaios.SessionMemoryStore
import ai.kaios.SQLiteMemoryStore
import ai.kaios.StoredProcess
import ai.kaios.StoredRunSnapshot
import ai.kaios.agent
import ai.kaios.builtInToolRegistry
import ai.kaios.workflow
import ai.kaios.Workflow
import ai.kaios.WorkflowScheduler
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val KAIOS_VERSION = "0.1.52"
private const val PROCESS_TRACE_SCHEMA = "kaios.process-trace/v1"
private const val RUN_CAPSULE_SCHEMA = "kaios.run-capsule/v1"
private const val DOCTOR_SCHEMA = "kaios.doctor/v1"
private const val RUNS_SCHEMA = "kaios.runs/v1"
private const val CONFIG_VALIDATION_SCHEMA = "kaios.config-validation/v1"
private const val BUG_REPORT_SCHEMA = "kaios.bug-report/v1"
private const val SETUP_SCHEMA = "kaios.setup/v1"
private const val VERIFY_SCHEMA = "kaios.verify/v1"

private val TOP_LEVEL_COMMANDS = listOf(
    "setup",
    "verify",
    "init",
    "demo",
    "run",
    "context",
    "index",
    "analyze",
    "config",
    "runs",
    "ps",
    "inspect",
    "trace",
    "capsule",
    "report",
    "export",
    "doctor",
    "bug-report",
    "version",
)

private val TOP_LEVEL_COMMAND_ALIASES = mapOf(
    "analyse" to "analyze",
    "ls" to "runs",
    "list" to "runs",
    "proc" to "ps",
    "process" to "ps",
    "status" to "doctor",
)

private val CONFIG_COMMANDS = listOf("templates", "validate", "show")

private val TRACE_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
}

private val CAPSULE_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun main(args: Array<String>) {
    val exitCode = KaiosCli().run(args, System.out, System.err)
    if (exitCode != 0) exitProcess(exitCode)
}

class KaiosCli(
    private val snapshotStore: FileRunSnapshotStore = FileRunSnapshotStore(defaultSnapshotRoot()),
    private val reportRoot: Path = defaultReportRoot(),
    private val reportRenderer: ProcessReportRenderer = ProcessReportRenderer(),
    private val artifactRoot: Path = defaultArtifactRoot(),
    private val capsuleRoot: Path = defaultCapsuleRoot(),
    private val artifactExporter: ArtifactExporter = ArtifactExporter(),
    private val snapshotRoot: Path = defaultSnapshotRoot(),
    private val workingDir: Path = Paths.get("").toAbsolutePath().normalize(),
    private val env: (String) -> String? = System::getenv,
) {
    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isEmpty()) {
            printUsage(out)
            return 0
        }

        val commandArgs = args.drop(1)
        return when (args.first()) {
            "setup" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("setup")) else setupProject(commandArgs, out, err)
            "verify" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("verify")) else verifyProject(commandArgs, out, err)
            "init" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("init")) else initProject(commandArgs, out, err)
            "demo" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("demo")) else runDemo(commandArgs, out, err)
            "run" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("run")) else runWorkflow(commandArgs, out, err)
            "context" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("context")) else previewContext(commandArgs, out, err)
            "index" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("index")) else previewIndex(commandArgs, out, err)
            "analyze" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("analyze")) else analyzeWorkspace(commandArgs, out, err)
            "config" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("config")) else configCommand(commandArgs, out, err)
            "runs" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("runs")) else listRuns(commandArgs, out, err)
            "ps" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("ps")) else printProcessTable(commandArgs, out, err)
            "inspect" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("inspect")) else inspectRun(commandArgs, out, err)
            "trace" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("trace")) else traceRun(commandArgs, out, err)
            "capsule" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("capsule")) else capsuleRun(commandArgs, out, err)
            "report" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("report")) else generateReport(commandArgs, out, err)
            "export" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("export")) else exportRun(commandArgs, out, err)
            "doctor" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("doctor")) else doctor(commandArgs, out, err)
            "bug-report" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("bug-report")) else bugReport(commandArgs, out, err)
            "version", "--version", "-V" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("version")) else {
                if (rejectUnexpectedArguments(commandArgs, "version", err)) 1 else version(out)
            }
            "help", "--help", "-h" -> {
                if (commandArgs.isNotEmpty() && !isHelp(commandArgs)) return printNamedCommandHelp(commandArgs, out, err)
                printUsage(out)
                0
            }
            else -> {
                err.println("Unknown command '${args.first()}'.")
                printSuggestion(err, args.first(), TOP_LEVEL_COMMANDS, TOP_LEVEL_COMMAND_ALIASES, "kaios")
                err.println("Run 'kaios help' for available commands.")
                printUsage(err)
                1
            }
        }
    }

    private fun isHelp(args: List<String>): Boolean =
        args.size == 1 && args.first() in setOf("help", "--help", "-h")

    private fun printCommandHelp(out: PrintStream, help: CommandHelp): Int {
        out.println("Usage: ${help.usage}")
        out.println(help.summary)
        if (help.examples.isNotEmpty()) {
            out.println()
            out.println("Examples:")
            help.examples.forEach { example -> out.println("  $example") }
        }
        if (help.notes.isNotEmpty()) {
            out.println()
            out.println("Notes:")
            help.notes.forEach { note -> out.println("  $note") }
        }
        out.println()
        out.println("Run 'kaios help' for all commands.")
        return 0
    }

    private fun printNamedCommandHelp(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val helpKey = helpCommandKey(args)
        if (helpKey == null) {
            err.println("Usage: kaios help <command>")
            err.println("Examples:")
            err.println("  kaios help run")
            err.println("  kaios help config show")
            err.println("Run 'kaios help' for available commands.")
            return 1
        }

        val help = commandHelpOrNull(helpKey)
        if (help == null) {
            err.println("Unknown command '${args.joinToString(" ")}'.")
            if (args.size == 2 && args.first() == "config") {
                printSuggestion(err, args.last(), CONFIG_COMMANDS, emptyMap(), "kaios help config")
            } else {
                printSuggestion(err, args.last(), TOP_LEVEL_COMMANDS, TOP_LEVEL_COMMAND_ALIASES, "kaios help")
            }
            err.println("Run 'kaios help' for available commands.")
            err.println("Usage: kaios help <command>")
            return 1
        }
        return printCommandHelp(out, help)
    }

    private fun helpCommandKey(args: List<String>): String? =
        when {
            args.size == 1 -> args.first()
            args.size == 2 && args.first() == "config" -> "config ${args[1]}"
            else -> null
        }

    private fun printCommandUsageError(err: PrintStream, command: String, message: String? = null): Int =
        printUsageError(err, commandUsage(command), command, message)

    private fun printUsageError(err: PrintStream, usage: String, helpCommand: String, message: String? = null): Int {
        if (!message.isNullOrBlank()) {
            err.println(message)
        }
        err.println("Usage: $usage")
        err.println("Run 'kaios help $helpCommand' for examples.")
        return 1
    }

    private fun rejectUnexpectedArguments(args: List<String>, command: String, err: PrintStream): Boolean {
        if (args.isEmpty()) return false
        printCommandUsageError(err, command, "Unexpected $command argument '${args.first()}'.")
        return true
    }

    private fun printSuggestion(
        err: PrintStream,
        input: String,
        candidates: List<String>,
        aliases: Map<String, String>,
        commandPrefix: String,
    ) {
        val suggestion = suggestedCommand(input, candidates, aliases) ?: return
        err.println("Did you mean '$commandPrefix $suggestion'?")
    }

    private fun suggestedCommand(input: String, candidates: List<String>, aliases: Map<String, String>): String? {
        val normalized = input.lowercase().trim()
        aliases[normalized]?.let { return it }
        if (normalized.length < 2) return null

        val maxDistance = if (normalized.length <= 3) 1 else 2
        return candidates
            .map { candidate -> candidate to levenshteinDistance(normalized, candidate) }
            .filter { (_, distance) -> distance in 1..maxDistance }
            .minWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first })
            ?.first
    }

    private fun levenshteinDistance(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (leftIndex in left.indices) {
            current[0] = leftIndex + 1
            for (rightIndex in right.indices) {
                val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + substitutionCost,
                )
            }
            val next = previous
            previous = current
            current = next
        }

        return previous[right.length]
    }

    private fun commandUsage(command: String): String =
        commandUsageOrNull(command) ?: error("Missing usage for command '$command'.")

    private fun commandUsageOrNull(command: String): String? =
        commandHelpOrNull(command)?.usage

    private fun commandHelp(command: String): CommandHelp =
        commandHelpOrNull(command) ?: error("Missing help for command '$command'.")

    private fun commandHelpOrNull(command: String): CommandHelp? =
        when (command) {
            "setup" -> CommandHelp(
                usage = "kaios setup [--template default|research|code-review|release] [--config kaios.json] [--ci] [--force] [--json|--format json]",
                summary = "Bootstrap a project workflow, validate it, and print the next useful commands.",
                examples = listOf(
                    "kaios setup",
                    "kaios setup --ci",
                    "kaios setup --template code-review --ci",
                    "kaios setup --json",
                ),
                notes = listOf(
                    "The default setup template is research because it is useful for project onboarding.",
                    "Existing config and CI files are kept unless --force is passed.",
                    "JSON output uses schema $SETUP_SCHEMA for automation.",
                ),
            )
            "verify" -> CommandHelp(
                usage = "kaios verify [--config kaios.json] [--json|--format json]",
                summary = "Run the one-command readiness gate for local projects and CI.",
                examples = listOf(
                    "kaios verify",
                    "kaios verify --config kaios.json",
                    "kaios verify --json",
                ),
                notes = listOf(
                    "The gate checks doctor diagnostics, validates project config, runs a deterministic mock smoke workflow, and validates the process trace contract.",
                    "It writes a normal run snapshot under .kaios/runs/ so ps, inspect, trace, capsule, and bug-report keep working.",
                    "JSON output uses schema $VERIFY_SCHEMA for CI and release gates.",
                ),
            )
            "init" -> CommandHelp(
                usage = "kaios init [--template default|research|code-review|release] [--config kaios.json] [--ci] [--force]",
                summary = "Create a local kaios.json workflow so runs use your own agent process graph.",
                examples = listOf(
                    "kaios init",
                    "kaios init --template research",
                    "kaios init --template research --ci",
                    "kaios init --template code-review --force",
                ),
                notes = listOf(
                    "Run 'kaios config templates' to see built-in workflow templates.",
                    "--ci also writes .github/workflows/kaios.yml with the one-command verify gate.",
                ),
            )
            "demo" -> CommandHelp(
                usage = "kaios demo",
                summary = "Run the no-key planner -> executor -> validator demo and print the agent process table.",
                examples = listOf("kaios demo"),
                notes = listOf(
                    "The demo always uses the deterministic mock provider.",
                    "It writes a Markdown artifact and kaios.process-trace/v1 JSON under .kaios/artifacts/.",
                ),
            )
            "run" -> CommandHelp(
                usage = "kaios run [--context path] [--index path] [--config kaios.json] [--out artifact.md] [--trace-out trace.json] [--force] \"task\"",
                summary = "Run an inspectable agent workflow and persist a snapshot under .kaios/runs/.",
                examples = listOf(
                    "kaios run \"summarize this project\"",
                    "kaios run --index . --out artifacts/project.md --force \"summarize this project\"",
                    "kaios run --index . --trace-out artifacts/trace.json --force \"summarize this project\"",
                    "kaios run --index . --context README.md --out artifacts/project.md --force \"explain the architecture\"",
                    "kaios run --config kaios.json \"review this release\"",
                ),
                notes = listOf(
                    "No API key is required by default; the mock provider is deterministic.",
                    "Use --trace-out to write kaios.process-trace/v1 JSON during the run.",
                    "Use -- before a task that starts with '-'.",
                    "After a run, use 'kaios ps latest', 'kaios inspect latest', 'kaios trace latest', and 'kaios capsule latest'.",
                ),
            )
            "context" -> CommandHelp(
                usage = "kaios context [path ...]",
                summary = "Preview bounded local files that would be attached to an agent run.",
                examples = listOf("kaios context README.md docs", "kaios context ."),
                notes = listOf("Generated directories and .kaiosignore matches are skipped."),
            )
            "index" -> CommandHelp(
                usage = "kaios index [path ...]",
                summary = "Render a deterministic Workspace Index with language stats and notable files.",
                examples = listOf("kaios index .", "kaios index README.md src docs"),
                notes = listOf("Use the same paths with 'kaios run --index' to orient a workflow without dumping file contents."),
            )
            "analyze" -> CommandHelp(
                usage = "kaios analyze [path ...] [--format markdown|json] [--out analysis.md] [--force]",
                summary = "Generate a no-key workspace report for onboarding, CI, or project handoff.",
                examples = listOf(
                    "kaios analyze .",
                    "kaios analyze . --out artifacts/analysis.md --force",
                    "kaios analyze . --format json --out artifacts/analysis.json --force",
                ),
            )
            "config" -> CommandHelp(
                usage = "kaios config <validate|show|templates> [--config kaios.json]",
                summary = "Validate, inspect, or list workflow configuration templates.",
                examples = listOf(
                    "kaios config templates",
                    "kaios config validate",
                    "kaios config show --config kaios.json",
                ),
            )
            "config templates" -> CommandHelp(
                usage = "kaios config templates",
                summary = "List built-in workflow templates that can be written with kaios init.",
                examples = listOf(
                    "kaios config templates",
                    "kaios init --template research",
                ),
            )
            "config validate" -> CommandHelp(
                usage = "kaios config validate [--config kaios.json] [--json|--format json]",
                summary = "Validate a kaios.json workflow without starting agents.",
                examples = listOf(
                    "kaios config validate",
                    "kaios config validate --config kaios.json",
                    "kaios config validate --json",
                ),
                notes = listOf("JSON output uses schema $CONFIG_VALIDATION_SCHEMA for CI and release gates."),
            )
            "config show" -> CommandHelp(
                usage = "kaios config show [--config kaios.json]",
                summary = "Print the workflow agents, tools, dependencies, fallback routes, and graph.",
                examples = listOf(
                    "kaios config show",
                    "kaios config show --config kaios.json",
                ),
            )
            "runs" -> CommandHelp(
                usage = "kaios runs [--json|--format json]",
                summary = "List saved run snapshots from .kaios/runs/.",
                examples = listOf(
                    "kaios runs",
                    "kaios runs --json",
                ),
                notes = listOf(
                    "Use a listed run id, or 'latest', with ps, inspect, trace, capsule, report, or export.",
                    "JSON output uses schema $RUNS_SCHEMA for Agent Desktop, CI, and local tooling.",
                ),
            )
            "ps" -> CommandHelp(
                usage = "kaios ps <run-id|latest>",
                summary = "Print the agent process table for a saved run.",
                examples = listOf(
                    "kaios ps latest",
                    "kaios ps run-97381ae9",
                ),
                notes = listOf("Tokens behave like CPU, context size like memory, and tool calls like syscalls."),
            )
            "inspect" -> CommandHelp(
                usage = "kaios inspect <run-id|latest>",
                summary = "Print final output and lifecycle events for a saved run.",
                examples = listOf(
                    "kaios inspect latest",
                    "kaios inspect run-97381ae9",
                ),
            )
            "trace" -> CommandHelp(
                usage = "kaios trace <run-id|latest> [--format text|json] [--out trace.json] [--force] [--check]",
                summary = "Print a KAI Process Trace with process metrics, execution path, event counts, and timeline.",
                examples = listOf(
                    "kaios trace latest",
                    "kaios trace latest --json",
                    "kaios trace latest --check",
                    "kaios trace run-97381ae9",
                    "kaios trace run-97381ae9 --json",
                    "kaios trace run-97381ae9 --json --out artifacts/trace.json --force",
                ),
                notes = listOf(
                    "Trace JSON uses schema $PROCESS_TRACE_SCHEMA for CI, UI, replay, and audit tooling.",
                    "Use --check in CI to validate the trace contract without writing an artifact.",
                    "Existing trace files are protected unless --force is passed.",
                ),
            )
            "capsule" -> CommandHelp(
                usage = "kaios capsule <run-id|latest>|--file capsule.json [--json] [--out capsule.json] [--force] [--check]",
                summary = "Build a portable run capsule with snapshot, trace, provenance hashes, and replay commands.",
                examples = listOf(
                    "kaios capsule latest",
                    "kaios capsule latest --check",
                    "kaios capsule latest --json",
                    "kaios capsule --file artifacts/run.capsule.json --check",
                    "kaios capsule run-97381ae9 --out artifacts/run.capsule.json --force",
                ),
                notes = listOf(
                    "Capsule JSON uses schema $RUN_CAPSULE_SCHEMA for audit, replay, CI, and future Agent Desktop imports.",
                    "The capsule is generated from an existing .kaios/runs snapshot; it does not re-run agents.",
                    "Use --file to validate a shared capsule without the original .kaios/runs snapshot.",
                    "Existing capsule files are protected unless --force is passed.",
                ),
            )
            "report" -> CommandHelp(
                usage = "kaios report <run-id|latest>",
                summary = "Generate a standalone HTML Agent Process Manager report.",
                examples = listOf(
                    "kaios report latest",
                    "kaios report run-97381ae9",
                ),
                notes = listOf("Reports are written under .kaios/reports/ by default."),
            )
            "export" -> CommandHelp(
                usage = "kaios export <run-id|latest> [--out artifact.md] [--force]",
                summary = "Export a saved run snapshot as a Markdown artifact.",
                examples = listOf(
                    "kaios export latest",
                    "kaios export run-97381ae9",
                    "kaios export run-97381ae9 --out artifacts/run.md --force",
                ),
            )
            "doctor" -> CommandHelp(
                usage = "kaios doctor [--json|--format json]",
                summary = "Check the local Java runtime, writable KAI OS directories, provider config, and project config.",
                examples = listOf(
                    "kaios doctor",
                    "kaios doctor --json",
                ),
                notes = listOf(
                    "Run this first when a command behaves differently across machines.",
                    "JSON output uses schema $DOCTOR_SCHEMA for CI and issue diagnostics.",
                ),
            )
            "bug-report" -> CommandHelp(
                usage = "kaios bug-report [--json|--format markdown|json] [--out report.md] [--force]",
                summary = "Generate a safe support report for GitHub issues and team handoff.",
                examples = listOf(
                    "kaios bug-report",
                    "kaios bug-report --out artifacts/kaios-bug-report.md --force",
                    "kaios bug-report --json",
                ),
                notes = listOf(
                    "The report includes doctor checks, project config validation, latest run summary, and trace contract status.",
                    "It does not print API keys or secret environment values.",
                    "JSON output uses schema $BUG_REPORT_SCHEMA for support automation.",
                ),
            )
            "version" -> CommandHelp(
                usage = "kaios --version",
                summary = "Print the installed KAI OS CLI version.",
                examples = listOf("kaios --version", "kaios version"),
            )
            else -> null
        }

    private fun runDemo(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isNotEmpty()) {
            return printCommandUsageError(err, "demo", "Demo does not accept arguments.")
        }

        val memory = SessionMemoryStore()
        val runtime = AgentRuntime()
        val tools = toolRegistry()
        val scheduler = WorkflowScheduler(
            runtime = runtime,
            modelProvider = MockModelProvider(),
            tools = tools,
            memory = memory,
        )
        val task = "show KAI OS as Agent=Process, Workflow=Scheduler, Tool=Syscall"
        val result = scheduler.run(defaultWorkflow(memory), task)
        val snapshotPath = snapshotStore.save(task, result)
        val snapshot = snapshotStore.load(result.runId)
        val artifactPath = runCatching { writeArtifact(snapshot, defaultArtifactPath(result.runId), false) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val tracePath = runCatching { writeTraceJson(snapshot, defaultTracePath(result.runId), false) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("KAI OS demo")
        out.println("provider: mock (deterministic, no API key)")
        out.println("run_id: ${result.runId.value}")
        out.println("success: ${result.success}")
        out.println("snapshot: $snapshotPath")
        out.println("artifact: $artifactPath")
        out.println("trace: $tracePath")
        out.println()
        out.println("processes:")
        out.println(formatProcessHeader())
        snapshot.processes.forEach { process -> out.println(formatProcess(process)) }
        out.println()
        out.println("output:")
        out.println(result.finalOutput)
        out.println()
        out.println("next:")
        out.println("  kaios ps latest")
        out.println("  kaios inspect latest")
        out.println("  kaios trace latest --json")
        out.println("  kaios capsule latest")
        out.println("  kaios run --index . --out artifacts/project.md --trace-out artifacts/trace.json --force \"summarize this project\"")
        return if (result.success) 0 else 2
    }

    private fun runWorkflow(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseRunCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "run", error.message)
        }

        val memory = runCatching { memoryStoreFromEnv(env) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val modelProvider = runCatching { modelProviderFromEnv(env) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val runtime = AgentRuntime()
        val tools = toolRegistry()
        val context = runCatching { contextLoader().load(command.contextPaths) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val workspaceIndex = runCatching {
            if (command.indexPaths.isEmpty()) WorkspaceIndex.Empty else workspaceIndexer().index(command.indexPaths)
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val configPath = command.configPath ?: defaultConfigPath().takeIf { !command.useBuiltInDefault && it.exists() }
        val workflow = runCatching {
            configPath?.let { loadProjectWorkflow(it, memory, tools) } ?: defaultWorkflow(memory)
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val scheduler = WorkflowScheduler(
            runtime = runtime,
            modelProvider = modelProvider,
            tools = tools,
            memory = memory,
        )

        val result = scheduler.run(workflow, composeRunInput(command.task, context, workspaceIndex))
        val path = snapshotStore.save(composeTaskSummary(command.task, context, workspaceIndex), result)
        val storedSnapshot = if (command.outputPath != null || command.traceOutputPath != null) {
            snapshotStore.load(result.runId)
        } else {
            null
        }
        val artifactPath = runCatching {
            command.outputPath?.let { outputPath ->
                writeArtifact(storedSnapshot ?: snapshotStore.load(result.runId), outputPath, command.forceOutput)
            }
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val tracePath = runCatching {
            command.traceOutputPath?.let { outputPath ->
                writeTraceJson(storedSnapshot ?: snapshotStore.load(result.runId), outputPath, command.forceOutput)
            }
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("run_id: ${result.runId.value}")
        out.println("success: ${result.success}")
        out.println("snapshot: $path")
        configPath?.let { out.println("config: $it") }
        if (context.sources.isNotEmpty()) {
            out.println("context: ${context.sources.size} file(s), ${context.totalChars} chars")
        }
        if (workspaceIndex.files.isNotEmpty()) {
            out.println("index: ${workspaceIndex.summary()}")
        }
        artifactPath?.let { out.println("artifact: $it") }
        tracePath?.let { out.println("trace: $it") }
        out.println()
        out.println(result.finalOutput)
        out.println()
        out.println("next:")
        out.println("  kaios ps latest")
        out.println("  kaios inspect latest")
        out.println("  kaios trace latest")
        out.println("  kaios capsule latest")
        out.println("  kaios report latest")
        out.println("  kaios export latest")
        return if (result.success) 0 else 2
    }

    private fun previewContext(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val paths = runCatching { parseContextCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "context", error.message)
        }

        val context = runCatching { contextLoader().load(paths) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("CONTEXT")
        out.println("root: $workingDir")
        out.println("files: ${context.sources.size}")
        out.println("chars: ${context.totalChars}/${context.maxChars}")
        out.println("truncated: ${context.truncated}")
        if (context.ignorePatternCount > 0) {
            out.println("ignore: .kaiosignore (${context.ignorePatternCount} pattern(s))")
        }
        out.println()
        out.println(formatContextHeader(context.sources))
        context.sources.forEach { source -> out.println(formatContextSource(source, context.sources)) }
        return 0
    }

    private fun previewIndex(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val paths = runCatching { parseIndexCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "index", error.message)
        }

        val index = runCatching { workspaceIndexer().index(paths) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println(index.render())
        return 0
    }

    private fun analyzeWorkspace(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseAnalyzeCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "analyze", error.message)
        }

        val index = runCatching { workspaceIndexer().index(command.paths) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val analyzer = WorkspaceAnalyzer()
        val report = when (command.format) {
            AnalyzeFormat.Markdown -> analyzer.render(index)
            AnalyzeFormat.Json -> analyzer.renderJson(index)
        }

        if (command.outputPath == null) {
            out.println(report)
            return 0
        }

        val path = runCatching { writeTextOutput(report, command.outputPath, command.force) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        out.println("analysis: $path")
        out.println("format: ${command.format.id}")
        out.println("index: ${index.summary()}")
        return 0
    }

    private fun setupProject(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseSetupCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "setup", error.message)
        }

        val template = requireProjectTemplate(command.templateId)
        val configPath = command.configPath
        val configFile = runCatching {
            setupConfigFile(configPath, command.templateId, command.force)
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val ciFile = runCatching {
            if (command.writeCi) {
                setupCiFile(defaultCiWorkflowPath(), configPath, command.force)
            } else {
                SetupFileReport(path = null, action = SetupFileAction.Skipped.id)
            }
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val validation = buildConfigValidationReport(configPath)
        val doctor = buildDoctorReport(configPath, runtimeConfigFailureStatus = DoctorStatus.WARN)
        val report = SetupReport(
            schema = SETUP_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            requestedTemplate = command.templateId,
            config = configFile,
            ci = ciFile,
            doctor = doctor,
            validation = validation,
            next = setupNextCommands(validation, ciFile, template),
        )

        when (command.format) {
            SetupFormat.Text -> renderSetupText(report, out)
            SetupFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (doctor.summary.failed > 0 || !validation.valid) 1 else 0
    }

    private fun setupConfigFile(path: Path, templateId: String, force: Boolean): SetupFileReport {
        val action = when {
            path.exists() && force -> SetupFileAction.Overwritten
            path.exists() -> SetupFileAction.Existing
            else -> SetupFileAction.Created
        }
        if (action != SetupFileAction.Existing) {
            path.parent?.let { Files.createDirectories(it) }
            path.writeText(projectConfigText(templateId))
        }
        return SetupFileReport(path = path.toString(), action = action.id)
    }

    private fun setupCiFile(path: Path, configPath: Path, force: Boolean): SetupFileReport {
        val action = when {
            path.exists() && force -> SetupFileAction.Overwritten
            path.exists() -> SetupFileAction.Existing
            else -> SetupFileAction.Created
        }
        if (action != SetupFileAction.Existing) {
            path.parent?.let { Files.createDirectories(it) }
            path.writeText(projectCiWorkflowText(configPath))
        }
        return SetupFileReport(path = path.toString(), action = action.id)
    }

    private fun setupNextCommands(
        validation: ConfigValidationReport,
        ciFile: SetupFileReport,
        template: KaiosProjectTemplate,
    ): List<String> =
        buildList {
            add("kaios config validate --config ${displayPath(Paths.get(validation.config))} --json")
            if (validation.valid) {
                add("kaios verify --config ${displayPath(Paths.get(validation.config))}")
                add("kaios ps latest")
            } else {
                add("fix ${displayPath(Paths.get(validation.config))} or rerun kaios setup --force")
            }
            if (ciFile.path != null) {
                add("git add ${displayPath(Paths.get(validation.config))} ${displayPath(Paths.get(ciFile.path))}")
            }
            add("kaios bug-report")
        }.distinct()

    private fun renderSetupText(report: SetupReport, out: PrintStream) {
        out.println("KAI OS setup")
        out.println("schema: ${report.schema}")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println("requested_template: ${report.requestedTemplate}")
        out.println("doctor: ${doctorSummaryText(report.doctor.summary)}")
        out.println("config: ${report.config.path}")
        out.println("config_action: ${report.config.action}")
        out.println("validation: ${if (report.validation.valid) "valid" else "invalid"}")
        out.println("workflow: ${report.validation.workflowName ?: "-"}")
        out.println("agents: ${report.validation.agentCount}")
        out.println("ci: ${report.ci.action}${report.ci.path?.let { " ($it)" }.orEmpty()}")
        val warnings = doctorWarnings(report.doctor)
        if (warnings.isNotEmpty()) {
            out.println()
            out.println("warnings:")
            warnings.forEach { warning -> out.println("  - $warning") }
        }
        if (report.validation.errors.isNotEmpty()) {
            out.println()
            out.println("errors:")
            report.validation.errors.forEach { error -> out.println("  - ${singleLine(error)}") }
        }
        out.println()
        out.println("next:")
        report.next.forEach { command -> out.println("  $command") }
    }

    private fun verifyProject(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseVerifyCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "verify", error.message)
        }
        val report = buildVerifyReport(command.configPath)

        when (command.format) {
            VerifyFormat.Text -> renderVerifyText(report, out)
            VerifyFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (report.status == "ready") 0 else 2
    }

    private fun buildVerifyReport(configPath: Path): VerifyReport {
        val doctor = buildDoctorReport(configPath, runtimeConfigFailureStatus = DoctorStatus.WARN)
        val config = buildConfigValidationReport(configPath)
        val errors = mutableListOf<String>()
        var run: VerifyRun? = null
        var trace: VerifyTrace? = null

        if (doctor.summary.failed > 0) {
            errors += "doctor failed with ${doctor.summary.failed} failed check(s)."
        }
        if (!config.valid) {
            errors += config.errors.ifEmpty { listOf("project config is invalid.") }
        }

        if (errors.isEmpty()) {
            val task = "verify KAI OS project workflow"
            runCatching {
                val memory = SessionMemoryStore()
                val tools = toolRegistry()
                val workflow = loadProjectWorkflow(configPath, memory, tools)
                val scheduler = WorkflowScheduler(
                    runtime = AgentRuntime(),
                    modelProvider = MockModelProvider(),
                    tools = tools,
                    memory = memory,
                )
                val result = scheduler.run(workflow, task)
                val snapshotPath = snapshotStore.save(task, result)
                val snapshot = snapshotStore.load(result.runId)
                val processTrace = buildProcessTrace(snapshot)
                val traceIssues = validateProcessTrace(processTrace)

                run = VerifyRun(
                    runId = snapshot.runId,
                    workflowName = snapshot.workflowName,
                    success = snapshot.success,
                    task = snapshot.task,
                    snapshot = snapshotPath.toString(),
                    processCount = snapshot.processes.size,
                    tokenTotal = snapshot.processes.sumOf { it.tokens },
                    syscallCount = snapshot.processes.sumOf { it.syscallCount },
                    contextBytes = snapshot.processes.sumOf { it.contextSize },
                    durationMillis = snapshot.processes.sumOf { it.durationMillis },
                )
                trace = VerifyTrace(
                    runId = processTrace.runId,
                    schema = processTrace.schema,
                    valid = traceIssues.isEmpty(),
                    processCount = processTrace.metrics.processCount,
                    eventCount = processTrace.metrics.eventCount,
                    issues = traceIssues,
                )
                if (!result.success) {
                    errors += "verify smoke workflow failed."
                }
                if (traceIssues.isNotEmpty()) {
                    errors += "process trace contract failed."
                }
            }.onFailure { error ->
                errors += error.message ?: "verify smoke workflow failed."
            }
        }

        val status = if (errors.isEmpty() && run?.success == true && trace?.valid == true) "ready" else "failed"
        return VerifyReport(
            schema = VERIFY_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            status = status,
            doctor = doctor,
            config = config,
            run = run,
            trace = trace,
            errors = errors.distinct(),
            next = verifyNextCommands(config, run, trace),
        )
    }

    private fun verifyNextCommands(
        config: ConfigValidationReport,
        run: VerifyRun?,
        trace: VerifyTrace?,
    ): List<String> =
        buildList {
            if (!config.valid) add("kaios setup --ci")
            if (run != null) {
                add("kaios ps latest")
                add("kaios inspect latest")
            }
            if (trace != null) {
                add(if (trace.valid) "kaios trace latest --check" else "kaios trace latest")
            }
            if (run != null) {
                add("kaios capsule latest --check")
            }
            add("kaios bug-report")
        }.distinct()

    private fun renderVerifyText(report: VerifyReport, out: PrintStream) {
        out.println("KAI OS verify")
        out.println("schema: ${report.schema}")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println("status: ${report.status}")
        out.println("doctor: ${doctorSummaryText(report.doctor.summary)}")
        out.println("config: ${if (report.config.valid) "valid" else "invalid"} (${report.config.config})")
        out.println("workflow: ${report.config.workflowName ?: "-"}")
        out.println("agents: ${report.config.agentCount}")
        val run = report.run
        if (run == null) {
            out.println("run: skipped")
        } else {
            out.println("run: ${run.runId} success=${run.success}")
            out.println("snapshot: ${run.snapshot}")
            out.println("tokens: ${run.tokenTotal}")
            out.println("syscalls: ${run.syscallCount}")
            out.println("memory: ${run.contextBytes}b")
            out.println("duration: ${run.durationMillis}ms")
        }
        val trace = report.trace
        if (trace == null) {
            out.println("trace: skipped")
        } else {
            out.println("trace: ${if (trace.valid) "valid" else "invalid"} (${trace.schema})")
            out.println("processes: ${trace.processCount}")
            out.println("events: ${trace.eventCount}")
        }
        val warnings = doctorWarnings(report.doctor)
        if (warnings.isNotEmpty()) {
            out.println()
            out.println("warnings:")
            warnings.forEach { warning -> out.println("  - $warning") }
        }
        if (report.errors.isNotEmpty()) {
            out.println()
            out.println("errors:")
            report.errors.forEach { error -> out.println("  - ${singleLine(error)}") }
        }
        out.println()
        out.println("next:")
        report.next.forEach { command -> out.println("  $command") }
    }

    private fun initProject(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseInitCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "init", error.message)
        }

        if (command.listTemplates) {
            printTemplates(out)
            return 0
        }

        val template = requireProjectTemplate(command.templateId)
        val path = command.configPath
        val ciPath = if (command.writeCi) defaultCiWorkflowPath() else null
        if (path.exists() && !command.force) {
            err.println("Config '$path' already exists. Use --force to overwrite it.")
            return 1
        }
        if (ciPath != null && ciPath.exists() && !command.force) {
            err.println("CI workflow '$ciPath' already exists. Use --force to overwrite it.")
            return 1
        }

        path.parent?.let { Files.createDirectories(it) }
        path.writeText(projectConfigText(command.templateId))
        ciPath?.let { workflowPath ->
            workflowPath.parent?.let { Files.createDirectories(it) }
            workflowPath.writeText(projectCiWorkflowText(path))
        }

        out.println("created: $path")
        ciPath?.let { out.println("created_ci: $it") }
        out.println("template: ${command.templateId}")
        out.println()
        out.println("next:")
        out.println("  kaios config validate --config ${displayPath(path)} --json")
        out.println("  kaios config show --config ${displayPath(path)}")
        out.println("  kaios verify --config ${displayPath(path)}")
        out.println("  kaios run \"${template.exampleTask}\"")
        ciPath?.let { out.println("  git add ${displayPath(path)} ${displayPath(it)}") }
        out.println("  kaios ps latest")
        return 0
    }

    private fun configCommand(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val subcommand = args.firstOrNull()
        if (subcommand == null) {
            return printCommandUsageError(err, "config")
        }

        return when (subcommand) {
            "templates" -> {
                val rest = args.drop(1)
                if (isHelp(rest)) return printCommandHelp(out, commandHelp("config templates"))
                if (rest.isNotEmpty()) {
                    return printUsageError(err, commandUsage("config templates"), "config templates", "Unknown config templates option '${rest.first()}'.")
                }
                printTemplates(out)
                0
            }
            "validate" -> {
                val rest = args.drop(1)
                if (isHelp(rest)) printCommandHelp(out, commandHelp("config validate")) else validateConfig(rest, out, err)
            }
            "show" -> {
                val rest = args.drop(1)
                if (isHelp(rest)) printCommandHelp(out, commandHelp("config show")) else showConfig(rest, out, err)
            }
            else -> {
                err.println("Unknown config command '$subcommand'.")
                printSuggestion(err, subcommand, CONFIG_COMMANDS, emptyMap(), "kaios config")
                printCommandUsageError(err, "config")
            }
        }
    }

    private fun validateConfig(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseConfigValidateCommand(args) }.getOrElse { error ->
            return printUsageError(err, commandUsage("config validate"), "config validate", error.message)
        }
        val path = command.configPath

        return runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }
            .fold(
                onSuccess = { workflow ->
                    if (command.format == ConfigValidationFormat.Json) {
                        out.println(TRACE_JSON.encodeToString(configValidationReport(path, workflow, emptyList())))
                    } else {
                        out.println("config: $path")
                        out.println("status: valid")
                        out.println("workflow: ${workflow.name}")
                        out.println("agents: ${workflow.nodes.size}")
                    }
                    0
                },
                onFailure = { error ->
                    if (command.format == ConfigValidationFormat.Json) {
                        out.println(TRACE_JSON.encodeToString(configValidationReport(path, null, listOf(error.message ?: "invalid project config"))))
                        1
                    } else {
                        printConfigLoadError(err, path, error)
                    }
                },
            )
    }

    private fun configValidationReport(path: Path, workflow: Workflow?, errors: List<String>): ConfigValidationReport =
        ConfigValidationReport(
            schema = CONFIG_VALIDATION_SCHEMA,
            config = path.toString(),
            valid = errors.isEmpty(),
            workflowName = workflow?.name,
            agentCount = workflow?.nodes?.size ?: 0,
            agentIds = workflow?.nodes?.map { it.id }.orEmpty(),
            errors = errors,
        )

    private fun showConfig(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val path = runCatching { parseConfigPath(args) }.getOrElse { error ->
            return printUsageError(err, commandUsage("config show"), "config show", error.message)
        }

        val workflow = runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }.getOrElse { error ->
            return printConfigLoadError(err, path, error)
        }

        out.println("config: $path")
        out.println("workflow: ${workflow.name}")
        out.println("agents:")
        workflow.nodes.forEach { node ->
            val tools = node.agent.allowedTools.sorted().ifEmpty { listOf("-") }.joinToString(",")
            val dependencies = node.dependencies.sorted().ifEmpty { listOf("-") }.joinToString(",")
            val fallback = node.fallback?.let { " fallback=$it" }.orEmpty()
            val fallbackOnly = if (node.fallbackOnly) " fallbackOnly=true" else ""
            val retries = if (node.maxAttempts > 1) " retries=${node.maxAttempts - 1}" else ""
            out.println("  ${node.id} tools=$tools dependsOn=$dependencies$fallback$fallbackOnly$retries")
        }
        out.println("graph:")
        workflow.nodes.forEach { node ->
            if (node.dependencies.isEmpty()) {
                out.println("  <input> -> ${node.id}")
            } else {
                node.dependencies.sorted().forEach { dependency ->
                    out.println("  $dependency -> ${node.id}")
                }
            }
        }
        return 0
    }

    private fun printConfigLoadError(err: PrintStream, path: Path, error: Throwable): Int {
        err.println(error.message)
        if (!path.exists()) {
            err.println("Run 'kaios init --template default' to create a local workflow config.")
            err.println("Run 'kaios config templates' to list available templates.")
            err.println("Use '--config path/to/kaios.json' to inspect another config file.")
        }
        return 1
    }

    private fun listRuns(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseRunsCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "runs", error.message)
        }
        val snapshots = snapshotStore.list()
        if (command.format == RunsFormat.Json) {
            out.println(TRACE_JSON.encodeToString(buildRunsReport(snapshots)))
            return 0
        }

        if (snapshots.isEmpty()) {
            out.println("No run snapshots found.")
            printNoRunSnapshotsHint(out)
            return 0
        }

        out.println("RUNS (${snapshots.size})")
        out.println("RUN ID        STATUS     WORKFLOW      PROCS  TOKENS  ALIAS   TASK")
        snapshots.forEachIndexed { index, snapshot ->
            val status = if (snapshot.success) "success" else "failed"
            val tokens = snapshot.processes.sumOf { it.tokens }
            val alias = if (index == 0) "latest" else "-"
            out.println(
                listOf(
                    snapshot.runId.padEnd(13),
                    status.padEnd(10),
                    snapshot.workflowName.padEnd(13),
                    snapshot.processes.size.toString().padEnd(6),
                    tokens.toString().padEnd(7),
                    alias.padEnd(8),
                    snapshot.task,
                ).joinToString(""),
            )
        }
        return 0
    }

    private fun buildRunsReport(snapshots: List<StoredRunSnapshot>): RunsReport =
        RunsReport(
            schema = RUNS_SCHEMA,
            count = snapshots.size,
            latestRunId = snapshots.firstOrNull()?.runId,
            runs = snapshots.mapIndexed { index, snapshot ->
                RunsReportItem(
                    runId = snapshot.runId,
                    alias = if (index == 0) "latest" else null,
                    status = if (snapshot.success) "success" else "failed",
                    success = snapshot.success,
                    workflowName = snapshot.workflowName,
                    task = snapshot.task,
                    processCount = snapshot.processes.size,
                    tokenTotal = snapshot.processes.sumOf { it.tokens },
                    syscallCount = snapshot.processes.sumOf { it.syscallCount },
                    contextBytes = snapshot.processes.sumOf { it.contextSize },
                    durationMillis = snapshot.processes.sumOf { it.durationMillis },
                )
            },
        )

    private fun printSnapshotLoadError(
        err: PrintStream,
        error: Throwable,
        knownSnapshots: List<StoredRunSnapshot>? = null,
    ): Int {
        err.println(error.message)

        val snapshots = knownSnapshots ?: runCatching { snapshotStore.list() }.getOrDefault(emptyList())
        if (snapshots.isEmpty()) {
            err.println("No run snapshots are available yet.")
            printNoRunSnapshotsHint(err)
            return 1
        }

        err.println("Run 'kaios runs' to list saved run ids.")
        err.println("Saved runs:")
        snapshots.take(3).forEach { snapshot ->
            val status = if (snapshot.success) "success" else "failed"
            err.println("  ${snapshot.runId}  $status  ${abbreviate(singleLine(snapshot.task), 80)}")
        }
        return 1
    }

    private fun printNoRunSnapshotsHint(out: PrintStream) {
        out.println("Run 'kaios demo' to create a no-key sample run.")
        out.println("Run 'kaios setup --ci' to create a project workflow.")
        out.println("Run 'kaios verify' to create an inspectable project run.")
    }

    private fun resolveRunId(value: String, knownSnapshots: List<StoredRunSnapshot>? = null): RunId {
        if (value != "latest") return RunId(value)

        val snapshots = knownSnapshots ?: snapshotStore.list()
        val snapshot = snapshots.firstOrNull() ?: error("Run snapshot 'latest' was not found.")
        return RunId(snapshot.runId)
    }

    private fun singleLine(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()

    private fun printProcessTable(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val runIdText = args.firstOrNull()
        if (runIdText == null) {
            return printCommandUsageError(err, "ps", "Run id is required.")
        }
        if (args.size > 1) {
            return printCommandUsageError(err, "ps", "Unexpected ps argument '${args[1]}'.")
        }
        val runId = runCatching { resolveRunId(runIdText) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }

        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }

        out.println("RUN ${snapshot.runId}  workflow=${snapshot.workflowName}  success=${snapshot.success}")
        out.println(formatProcessHeader())
        snapshot.processes.forEach { out.println(formatProcess(it)) }
        return 0
    }

    private fun generateReport(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val runIdText = args.firstOrNull()
        if (runIdText == null) {
            return printCommandUsageError(err, "report", "Run id is required.")
        }
        if (args.size > 1) {
            return printCommandUsageError(err, "report", "Unexpected report argument '${args[1]}'.")
        }

        val snapshots = snapshotStore.list()
        val runId = runCatching { resolveRunId(runIdText, snapshots) }.getOrElse {
            return printSnapshotLoadError(err, it, snapshots)
        }
        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            return printSnapshotLoadError(err, it, snapshots)
        }
        val allRuns = if (snapshots.any { it.runId == snapshot.runId }) snapshots else listOf(snapshot) + snapshots

        Files.createDirectories(reportRoot)
        allRuns.forEach { run ->
            reportRoot.resolve("${run.runId}.html").writeText(reportRenderer.render(run, allRuns))
        }

        val reportPath = reportRoot.resolve("${snapshot.runId}.html").toAbsolutePath().normalize()
        out.println("report: $reportPath")
        out.println("open: $reportPath")
        return 0
    }

    private fun exportRun(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseExportCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "export", error.message)
        }

        val runId = runCatching { resolveRunId(command.runIdText) }.getOrElse { error ->
            return printSnapshotLoadError(err, error)
        }
        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse { error ->
            return printSnapshotLoadError(err, error)
        }
        val outputPath = command.outputPath ?: defaultArtifactPath(runId)

        val path = runCatching { writeArtifact(snapshot, outputPath, command.force) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("artifact: $path")
        return 0
    }

    private fun doctor(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseDoctorCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "doctor", error.message)
        }
        val report = buildDoctorReport()

        when (command.format) {
            DoctorFormat.Text -> renderDoctorText(report, out)
            DoctorFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (report.summary.failed > 0) 2 else 0
    }

    private fun bugReport(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseBugReportCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "bug-report", error.message)
        }
        val report = buildBugReport()
        val rendered = when (command.format) {
            BugReportFormat.Markdown -> renderBugReportMarkdown(report)
            BugReportFormat.Json -> TRACE_JSON.encodeToString(report)
        }

        val outputPath = command.outputPath
        if (outputPath == null) {
            out.println(rendered)
        } else {
            val path = runCatching { writeTextOutput("$rendered\n", outputPath, command.force) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
            out.println("bug_report: $path")
            out.println("format: ${command.format.id}")
            out.println("schema: ${report.schema}")
        }

        return 0
    }

    private fun buildBugReport(): BugReport {
        val doctor = buildDoctorReport()
        val config = buildConfigValidationReport(defaultConfigPath())
        val snapshots = runCatching { snapshotStore.list() }.getOrElse { emptyList() }
        val latestSnapshot = snapshots.firstOrNull()
        val latestRun = latestSnapshot?.let(::bugReportRun)
        val trace = latestSnapshot?.let(::bugReportTrace)

        return BugReport(
            schema = BUG_REPORT_SCHEMA,
            version = KAIOS_VERSION,
            generatedAt = Instant.now().toString(),
            cwd = workingDir.toString(),
            files = BugReportFiles(
                config = defaultConfigPath().toString(),
                runsDir = snapshotRoot.toAbsolutePath().normalize().toString(),
                reportsDir = reportRoot.toAbsolutePath().normalize().toString(),
                artifactsDir = artifactRoot.toAbsolutePath().normalize().toString(),
            ),
            doctor = doctor,
            config = config,
            latestRun = latestRun,
            trace = trace,
            next = bugReportNextCommands(config, latestRun),
        )
    }

    private fun buildConfigValidationReport(path: Path): ConfigValidationReport =
        runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }
            .fold(
                onSuccess = { workflow -> configValidationReport(path, workflow, emptyList()) },
                onFailure = { error -> configValidationReport(path, null, listOf(error.message ?: "invalid project config")) },
            )

    private fun bugReportRun(snapshot: StoredRunSnapshot): BugReportRun =
        BugReportRun(
            runId = snapshot.runId,
            workflowName = snapshot.workflowName,
            success = snapshot.success,
            task = snapshot.task,
            processCount = snapshot.processes.size,
            tokenTotal = snapshot.processes.sumOf { it.tokens },
            syscallCount = snapshot.processes.sumOf { it.syscallCount },
            contextBytes = snapshot.processes.sumOf { it.contextSize },
            durationMillis = snapshot.processes.sumOf { it.durationMillis },
        )

    private fun bugReportTrace(snapshot: StoredRunSnapshot): BugReportTrace {
        val trace = buildProcessTrace(snapshot)
        val issues = validateProcessTrace(trace)
        return BugReportTrace(
            runId = trace.runId,
            schema = trace.schema,
            valid = issues.isEmpty(),
            processCount = trace.metrics.processCount,
            eventCount = trace.metrics.eventCount,
            issues = issues,
        )
    }

    private fun bugReportNextCommands(config: ConfigValidationReport, latestRun: BugReportRun?): List<String> =
        buildList {
            if (!config.valid) {
                add("kaios setup --ci")
            } else {
                add("kaios verify --config ${displayPath(Paths.get(config.config))}")
            }
            if (latestRun == null) {
                add("kaios demo")
            } else {
                add("kaios ps latest")
                add("kaios trace latest --check")
                add("kaios capsule latest --check")
                add("kaios inspect latest")
            }
            add("kaios doctor --json")
        }.distinct()

    private fun renderBugReportMarkdown(report: BugReport): String = buildString {
        appendLine("# KAI OS Bug Report")
        appendLine()
        appendLine("> Safe to paste into a GitHub issue. Do not add API keys, tokens, or private prompts.")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- schema: `${report.schema}`")
        appendLine("- version: `${report.version}`")
        appendLine("- generated_at: `${report.generatedAt}`")
        appendLine("- cwd: `${report.cwd}`")
        appendLine()
        appendLine("## What Happened")
        appendLine()
        appendLine("- Expected:")
        appendLine("- Actual:")
        appendLine("- Command:")
        appendLine()
        appendLine("## Doctor")
        appendLine()
        appendLine("- status: `${report.doctor.summary.status}`")
        appendLine("- failed: `${report.doctor.summary.failed}`")
        appendLine("- warnings: `${report.doctor.summary.warnings}`")
        report.doctor.checks.forEach { check ->
            appendLine("- [${check.status}] ${check.name}: ${singleLine(check.detail)}")
        }
        appendLine()
        appendLine("## Project Config")
        appendLine()
        appendLine("- config: `${report.config.config}`")
        appendLine("- valid: `${report.config.valid}`")
        appendLine("- workflow: `${report.config.workflowName ?: "-"}`")
        appendLine("- agents: `${report.config.agentCount}`")
        if (report.config.agentIds.isNotEmpty()) {
            appendLine("- agent_ids: `${report.config.agentIds.joinToString(", ")}`")
        }
        if (report.config.errors.isNotEmpty()) {
            appendLine("- errors:")
            report.config.errors.forEach { error -> appendLine("  - ${singleLine(error)}") }
        }
        appendLine()
        appendLine("## Latest Run")
        appendLine()
        if (report.latestRun == null) {
            appendLine("No saved run snapshot was found.")
        } else {
            appendLine("- run_id: `${report.latestRun.runId}`")
            appendLine("- workflow: `${report.latestRun.workflowName}`")
            appendLine("- success: `${report.latestRun.success}`")
            appendLine("- task: `${singleLine(report.latestRun.task)}`")
            appendLine("- processes: `${report.latestRun.processCount}`")
            appendLine("- tokens: `${report.latestRun.tokenTotal}`")
            appendLine("- syscalls: `${report.latestRun.syscallCount}`")
            appendLine("- context_bytes: `${report.latestRun.contextBytes}`")
            appendLine("- duration_ms: `${report.latestRun.durationMillis}`")
        }
        appendLine()
        appendLine("## Trace Contract")
        appendLine()
        if (report.trace == null) {
            appendLine("No trace could be built because there is no saved run snapshot.")
        } else {
            appendLine("- run_id: `${report.trace.runId}`")
            appendLine("- schema: `${report.trace.schema}`")
            appendLine("- valid: `${report.trace.valid}`")
            appendLine("- processes: `${report.trace.processCount}`")
            appendLine("- events: `${report.trace.eventCount}`")
            if (report.trace.issues.isNotEmpty()) {
                appendLine("- issues:")
                report.trace.issues.forEach { issue -> appendLine("  - ${singleLine(issue)}") }
            }
        }
        appendLine()
        appendLine("## Files")
        appendLine()
        appendLine("- config: `${report.files.config}`")
        appendLine("- runs_dir: `${report.files.runsDir}`")
        appendLine("- reports_dir: `${report.files.reportsDir}`")
        appendLine("- artifacts_dir: `${report.files.artifactsDir}`")
        appendLine()
        appendLine("## Next Commands")
        appendLine()
        report.next.forEach { command -> appendLine("- `$command`") }
    }.trimEnd()

    private fun buildDoctorReport(
        configPath: Path = defaultConfigPath(),
        runtimeConfigFailureStatus: DoctorStatus = DoctorStatus.FAIL,
    ): DoctorReport {
        val checks = listOf(
            javaCheck(),
            directoryCheck("runs directory", snapshotRoot),
            directoryCheck("reports directory", reportRoot),
            directoryCheck("artifacts directory", artifactRoot),
            modelProviderCheck(runtimeConfigFailureStatus),
            httpAllowlistCheck(),
            memoryStoreCheck(runtimeConfigFailureStatus),
            configCheck(configPath),
            snapshotsCheck(),
        )

        val failed = checks.count { it.status == DoctorStatus.FAIL }
        val warnings = checks.count { it.status == DoctorStatus.WARN }
        val next = doctorNextCommands(failed, configPath)

        return DoctorReport(
            schema = DOCTOR_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            summary = DoctorSummary(
                status = if (failed > 0) "failed" else "ready",
                failed = failed,
                warnings = warnings,
            ),
            checks = checks.map { check ->
                DoctorReportCheck(
                    name = check.name,
                    status = check.status.name,
                    detail = check.detail,
                )
            },
            next = next,
        )
    }

    private fun doctorNextCommands(failed: Int, configPath: Path): List<String> =
        buildList {
            if (failed > 0) add("fix failed checks above")
            add("kaios demo")
            if (configPath.exists()) {
                add("kaios verify --config ${displayPath(configPath)}")
            } else if (configPath == defaultConfigPath()) {
                add("kaios setup --ci")
            } else {
                add("kaios setup --config ${displayPath(configPath)} --ci")
            }
            add("kaios analyze . --out artifacts/analysis.md --force")
        }.distinct()

    private fun doctorSummaryText(summary: DoctorSummary): String =
        when {
            summary.failed > 0 -> "failed (${summary.failed} failed, ${summary.warnings} warning(s))"
            summary.warnings > 0 -> "ready with ${summary.warnings} warning(s)"
            else -> summary.status
        }

    private fun doctorWarnings(report: DoctorReport): List<String> =
        report.checks
            .filter { check -> check.status == DoctorStatus.WARN.name }
            .map { check -> "${check.name}: ${singleLine(check.detail)}" }

    private fun renderDoctorText(report: DoctorReport, out: PrintStream) {
        out.println("KAI OS doctor")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println()
        report.checks.forEach { check ->
            out.println("[${check.status}] ${check.name}: ${check.detail}")
        }
        out.println()

        when {
            report.summary.failed > 0 -> out.println("summary: ${report.summary.failed} failed, ${report.summary.warnings} warning(s)")
            report.summary.warnings > 0 -> out.println("summary: ready with ${report.summary.warnings} warning(s)")
            else -> out.println("summary: ready")
        }
        out.println()
        out.println("next:")
        report.next.forEach { next -> out.println("  $next") }
    }

    private fun version(out: PrintStream): Int {
        out.println("kaios $KAIOS_VERSION")
        return 0
    }

    private fun javaCheck(): DoctorCheck {
        val version = System.getProperty("java.version").orEmpty()
        val major = javaMajorVersion(version)
        return when {
            major == null -> DoctorCheck("Java runtime", DoctorStatus.WARN, "could not parse version '$version'")
            major >= 17 -> DoctorCheck("Java runtime", DoctorStatus.OK, "$version")
            else -> DoctorCheck("Java runtime", DoctorStatus.FAIL, "$version found; Java 17+ is required")
        }
    }

    private fun directoryCheck(name: String, path: Path): DoctorCheck =
        runCatching {
            path.createDirectories()
            val probe = Files.createTempFile(path, ".kaios-doctor-", ".tmp")
            probe.deleteIfExists()
            DoctorCheck(name, DoctorStatus.OK, "${path.toAbsolutePath().normalize()} writable")
        }.getOrElse { error ->
            DoctorCheck(name, DoctorStatus.FAIL, "${path.toAbsolutePath().normalize()} not writable (${error.message})")
        }

    private fun modelProviderCheck(failureStatus: DoctorStatus): DoctorCheck {
        val selected = env("KAIOS_MODEL_PROVIDER")?.lowercase()?.trim().orEmpty().ifBlank { "mock" }
        return runCatching { modelProviderFromEnv(env) }
            .fold(
                onSuccess = {
                    val detail = when (selected) {
                        "mock" -> "mock (deterministic local provider, no API key needed)"
                        "openai", "openai-compatible" -> "openai-compatible (${env("OPENAI_MODEL")})"
                        "ollama" -> "ollama (${env("OLLAMA_MODEL")})"
                        else -> selected
                    }
                    DoctorCheck("model provider", DoctorStatus.OK, detail)
                },
                onFailure = { error ->
                    DoctorCheck("model provider", failureStatus, error.message ?: "invalid model provider configuration")
                },
            )
    }

    private fun memoryStoreCheck(failureStatus: DoctorStatus): DoctorCheck {
        val selected = env("KAIOS_MEMORY_STORE")?.lowercase()?.trim().orEmpty().ifBlank { "session" }
        return runCatching { memoryStoreFromEnv(env) }
            .fold(
                onSuccess = {
                    val detail = when (selected) {
                        "session" -> "session (in-memory process memory)"
                        "sqlite" -> "sqlite (${env("KAIOS_SQLITE_PATH") ?: Paths.get(".kaios", "kaios.db")})"
                        else -> selected
                    }
                    DoctorCheck("memory store", DoctorStatus.OK, detail)
                },
                onFailure = { error ->
                    DoctorCheck("memory store", failureStatus, error.message ?: "invalid memory store configuration")
                },
            )
    }

    private fun httpAllowlistCheck(): DoctorCheck {
        val allowlist = httpAllowlist()
        val detail = if (allowlist.isEmpty()) {
            "disabled (set KAIOS_HTTP_ALLOWLIST to enable real HTTP syscalls)"
        } else {
            "${allowlist.size} allowlist rule(s): ${allowlist.joinToString(", ")}"
        }
        return DoctorCheck("http syscall", DoctorStatus.OK, detail)
    }

    private fun snapshotsCheck(): DoctorCheck =
        runCatching { snapshotStore.list() }
            .fold(
                onSuccess = { snapshots ->
                    val detail = if (snapshotRoot.exists()) {
                        "${snapshots.size} run snapshot(s) under ${snapshotRoot.toAbsolutePath().normalize()}"
                    } else {
                        "no run directory yet (${snapshotRoot.toAbsolutePath().normalize()})"
                    }
                    DoctorCheck("run snapshots", DoctorStatus.OK, detail)
                },
                onFailure = { error ->
                    DoctorCheck("run snapshots", DoctorStatus.WARN, error.message ?: "could not list run snapshots")
                },
            )

    private fun configCheck(path: Path): DoctorCheck {
        if (!path.exists()) {
            return if (path == defaultConfigPath()) {
                DoctorCheck("project config", DoctorStatus.OK, "no $KAIOS_CONFIG_FILE found; using built-in default workflow")
            } else {
                DoctorCheck("project config", DoctorStatus.FAIL, "config file '$path' was not found")
            }
        }

        return runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }
            .fold(
                onSuccess = { workflow ->
                    DoctorCheck(
                        "project config",
                        DoctorStatus.OK,
                        "$path (${workflow.nodes.size} agent process node(s))",
                    )
                },
                onFailure = { error ->
                    DoctorCheck("project config", DoctorStatus.FAIL, error.message ?: "invalid project config")
                },
            )
    }

    private fun inspectRun(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val runIdText = args.firstOrNull()
        if (runIdText == null) {
            return printCommandUsageError(err, "inspect", "Run id is required.")
        }
        if (args.size > 1) {
            return printCommandUsageError(err, "inspect", "Unexpected inspect argument '${args[1]}'.")
        }
        val runId = runCatching { resolveRunId(runIdText) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }

        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }

        out.println("RUN ${snapshot.runId}")
        out.println("workflow: ${snapshot.workflowName}")
        out.println("task: ${snapshot.task}")
        out.println("success: ${snapshot.success}")
        out.println()
        out.println("output:")
        out.println(snapshot.finalOutput)
        out.println()
        out.println("events:")
        snapshot.events.forEach { event ->
            out.println("${event.timestamp} pid=${event.pid} agent=${event.agent} ${event.type} ${event.message}")
        }
        return 0
    }

    private fun traceRun(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseTraceCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "trace", error.message)
        }

        val runId = runCatching { resolveRunId(command.runIdText) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }
        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }

        val trace = buildProcessTrace(snapshot)
        if (command.check) {
            return checkProcessTrace(trace, out, err)
        }

        val rendered = when (command.format) {
            TraceFormat.Text -> renderProcessTrace(trace)
            TraceFormat.Json -> TRACE_JSON.encodeToString(trace)
        }

        val outputPath = command.outputPath
        if (outputPath == null) {
            out.println(rendered)
        } else {
            val path = runCatching { writeTextOutput("$rendered\n", outputPath, command.forceOutput) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
            out.println("trace: $path")
            out.println("format: ${command.format.id}")
            out.println("schema: ${trace.schema}")
        }
        return 0
    }

    private fun capsuleRun(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseCapsuleCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "capsule", error.message)
        }

        val capsule = if (command.inputPath != null) {
            runCatching { loadRunCapsule(command.inputPath) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
        } else {
            val runIdText = command.runIdText ?: return printCommandUsageError(err, "capsule", "Run id or --file is required.")
            val runId = runCatching { resolveRunId(runIdText) }.getOrElse {
                return printSnapshotLoadError(err, it)
            }
            val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
                return printSnapshotLoadError(err, it)
            }
            buildRunCapsule(snapshot)
        }

        if (command.check) {
            return checkRunCapsule(capsule, out, err)
        }

        val rendered = CAPSULE_JSON.encodeToString(capsule)
        if (command.printJson) {
            out.println(rendered)
            return 0
        }

        val outputPath = command.outputPath ?: if (command.inputPath == null) {
            defaultCapsulePath(RunId(capsule.run.runId))
        } else {
            null
        }
        if (outputPath != null) {
            val path = runCatching { writeTextOutput("$rendered\n", outputPath, command.forceOutput) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
            printRunCapsuleSummary(capsule, out, path.toString())
            return 0
        }

        printRunCapsuleSummary(capsule, out, command.inputPath.toString())
        return 0
    }

    private fun printRunCapsuleSummary(capsule: RunCapsule, out: PrintStream, capsulePath: String) {
        out.println("capsule: $capsulePath")
        out.println("schema: ${capsule.schema}")
        out.println("run: ${capsule.run.runId}")
        out.println("valid: ${capsule.validation.valid}")
        out.println("snapshot_sha256: ${capsule.provenance.snapshotSha256}")
        capsule.provenance.embeddedSnapshotSha256?.let { out.println("embedded_snapshot_sha256: $it") }
        out.println("trace_sha256: ${capsule.provenance.traceSha256}")
        out.println("next:")
        out.println("  kaios capsule ${capsule.run.runId} --check")
        out.println("  kaios trace ${capsule.run.runId} --check")
    }

    private fun loadRunCapsule(path: Path): RunCapsule {
        require(path.exists()) { "Capsule file '$path' was not found." }
        return CAPSULE_JSON.decodeFromString(Files.readString(path))
    }

    private fun buildRunCapsule(snapshot: StoredRunSnapshot): RunCapsule {
        val runId = RunId(snapshot.runId)
        val snapshotPath = snapshotStore.pathFor(runId).toAbsolutePath().normalize()
        val snapshotText = runCatching { Files.readString(snapshotPath) }
            .getOrElse { CAPSULE_JSON.encodeToString(snapshot) }
        val trace = buildProcessTrace(snapshot)
        val traceText = CAPSULE_JSON.encodeToString(trace)
        val traceIssues = validateProcessTrace(trace)
        val configPath = defaultConfigPath()
        val configText = if (configPath.exists()) {
            runCatching { Files.readString(configPath) }.getOrNull()
        } else {
            null
        }
        val config = buildConfigValidationReport(configPath)
        val capsule = RunCapsule(
            schema = RUN_CAPSULE_SCHEMA,
            version = KAIOS_VERSION,
            generatedAt = Instant.now().toString(),
            cwd = workingDir.toString(),
            run = RunCapsuleRun(
                runId = snapshot.runId,
                workflowName = snapshot.workflowName,
                success = snapshot.success,
                task = snapshot.task,
                processCount = snapshot.processes.size,
                tokenTotal = snapshot.processes.sumOf { it.tokens },
                syscallCount = snapshot.processes.sumOf { it.syscallCount },
                contextBytes = snapshot.processes.sumOf { it.contextSize },
                durationMillis = snapshot.processes.sumOf { it.durationMillis },
            ),
            provenance = RunCapsuleProvenance(
                snapshotPath = snapshotPath.toString(),
                snapshotSha256 = sha256Hex(snapshotText),
                embeddedSnapshotSha256 = sha256Hex(CAPSULE_JSON.encodeToString(snapshot)),
                traceSha256 = sha256Hex(traceText),
                configPath = configPath.toAbsolutePath().normalize().toString(),
                configSha256 = configText?.let(::sha256Hex),
                configValid = config.valid,
                configWorkflowName = config.workflowName,
                configAgentIds = config.agentIds,
            ),
            replay = RunCapsuleReplay(
                commands = listOf(
                    "kaios ps ${snapshot.runId}",
                    "kaios inspect ${snapshot.runId}",
                    "kaios trace ${snapshot.runId} --check",
                    "kaios report ${snapshot.runId}",
                    "kaios export ${snapshot.runId}",
                ),
                note = "Capsules are generated from saved run snapshots and can be inspected without re-running agents.",
            ),
            validation = RunCapsuleValidation(
                valid = traceIssues.isEmpty(),
                issues = traceIssues,
                checkedAt = Instant.now().toString(),
            ),
            snapshot = snapshot,
            trace = trace,
        )
        val issues = validateRunCapsule(capsule)
        return capsule.copy(validation = capsule.validation.copy(valid = issues.isEmpty(), issues = issues))
    }

    private fun checkRunCapsule(capsule: RunCapsule, out: PrintStream, err: PrintStream): Int {
        val issues = validateRunCapsule(capsule)
        if (issues.isEmpty()) {
            out.println("capsule: ${capsule.run.runId}")
            out.println("schema: ${capsule.schema}")
            out.println("status: valid")
            out.println("snapshot_sha256: ${capsule.provenance.snapshotSha256}")
            out.println("trace_sha256: ${capsule.provenance.traceSha256}")
            out.println("processes: ${capsule.run.processCount}")
            out.println("events: ${capsule.trace.metrics.eventCount}")
            return 0
        }

        err.println("capsule: ${capsule.run.runId}")
        err.println("schema: ${capsule.schema}")
        err.println("status: invalid")
        err.println("issues:")
        issues.forEach { issue -> err.println("  - $issue") }
        return 2
    }

    private fun validateRunCapsule(capsule: RunCapsule): List<String> {
        val issues = mutableListOf<String>()
        fun requireCapsule(condition: Boolean, message: String) {
            if (!condition) issues += message
        }

        requireCapsule(capsule.schema == RUN_CAPSULE_SCHEMA, "schema must be $RUN_CAPSULE_SCHEMA.")
        requireCapsule(capsule.version.isNotBlank(), "version must not be blank.")
        requireCapsule(capsule.run.runId.isNotBlank(), "run.runId must not be blank.")
        requireCapsule(capsule.snapshot.runId == capsule.run.runId, "snapshot.runId must match run.runId.")
        requireCapsule(capsule.trace.runId == capsule.run.runId, "trace.runId must match run.runId.")
        requireCapsule(capsule.snapshot.workflowName == capsule.run.workflowName, "snapshot.workflowName must match run.workflowName.")
        requireCapsule(capsule.trace.workflowName == capsule.run.workflowName, "trace.workflowName must match run.workflowName.")
        requireCapsule(capsule.run.processCount == capsule.snapshot.processes.size, "run.processCount must equal snapshot.processes.size.")
        requireCapsule(capsule.run.processCount == capsule.trace.metrics.processCount, "run.processCount must equal trace.metrics.processCount.")
        requireCapsule(capsule.run.tokenTotal == capsule.trace.metrics.tokenTotal, "run.tokenTotal must equal trace.metrics.tokenTotal.")
        requireCapsule(capsule.run.syscallCount == capsule.trace.metrics.syscallCount, "run.syscallCount must equal trace.metrics.syscallCount.")
        requireCapsule(capsule.run.contextBytes == capsule.trace.metrics.contextBytes, "run.contextBytes must equal trace.metrics.contextBytes.")
        requireCapsule(capsule.provenance.snapshotSha256.length == 64, "provenance.snapshotSha256 must be a SHA-256 hex digest.")
        capsule.provenance.embeddedSnapshotSha256?.let { embeddedSnapshotSha256 ->
            requireCapsule(embeddedSnapshotSha256.length == 64, "provenance.embeddedSnapshotSha256 must be a SHA-256 hex digest.")
            requireCapsule(
                embeddedSnapshotSha256 == sha256Hex(CAPSULE_JSON.encodeToString(capsule.snapshot)),
                "provenance.embeddedSnapshotSha256 must match the embedded snapshot.",
            )
        }
        requireCapsule(capsule.provenance.traceSha256.length == 64, "provenance.traceSha256 must be a SHA-256 hex digest.")
        requireCapsule(
            capsule.provenance.traceSha256 == sha256Hex(CAPSULE_JSON.encodeToString(capsule.trace)),
            "provenance.traceSha256 must match the embedded trace.",
        )
        val snapshotPath = Paths.get(capsule.provenance.snapshotPath)
        if (snapshotPath.exists()) {
            requireCapsule(
                capsule.provenance.snapshotSha256 == sha256Hex(Files.readString(snapshotPath)),
                "provenance.snapshotSha256 must match the saved snapshot file.",
            )
        }
        requireCapsule(
            capsule.replay.commands.any { it == "kaios trace ${capsule.run.runId} --check" },
            "replay.commands must include trace contract validation.",
        )

        val traceIssues = validateProcessTrace(capsule.trace)
        requireCapsule(
            capsule.validation.valid == traceIssues.isEmpty(),
            "validation.valid must match the trace contract result.",
        )
        traceIssues.forEach { issue -> requireCapsule(false, "trace: $issue") }

        return issues.distinct()
    }

    private fun checkProcessTrace(trace: ProcessTrace, out: PrintStream, err: PrintStream): Int {
        val issues = validateProcessTrace(trace)
        if (issues.isEmpty()) {
            out.println("trace: ${trace.runId}")
            out.println("schema: ${trace.schema}")
            out.println("status: valid")
            out.println("processes: ${trace.metrics.processCount}")
            out.println("events: ${trace.metrics.eventCount}")
            return 0
        }

        err.println("trace: ${trace.runId}")
        err.println("schema: ${trace.schema}")
        err.println("status: invalid")
        err.println("issues:")
        issues.forEach { issue -> err.println("  - $issue") }
        return 2
    }

    private fun validateProcessTrace(trace: ProcessTrace): List<String> {
        val issues = mutableListOf<String>()
        fun requireTrace(condition: Boolean, message: String) {
            if (!condition) issues += message
        }

        requireTrace(trace.schema == PROCESS_TRACE_SCHEMA, "schema must be $PROCESS_TRACE_SCHEMA.")
        requireTrace(trace.runId.isNotBlank(), "runId must not be blank.")
        requireTrace(trace.workflowName.isNotBlank(), "workflowName must not be blank.")
        requireTrace(trace.metrics.processCount == trace.processes.size, "metrics.processCount must equal processes.size.")
        requireTrace(trace.metrics.eventCount == trace.events.size, "metrics.eventCount must equal events.size.")
        requireTrace(trace.metrics.tokenTotal == trace.processes.sumOf { it.tokens }, "metrics.tokenTotal must equal the process token sum.")
        requireTrace(trace.metrics.inputTokens == trace.processes.sumOf { it.inputTokens }, "metrics.inputTokens must equal the process input token sum.")
        requireTrace(trace.metrics.outputTokens == trace.processes.sumOf { it.outputTokens }, "metrics.outputTokens must equal the process output token sum.")
        requireTrace(trace.metrics.contextBytes == trace.processes.sumOf { it.contextBytes }, "metrics.contextBytes must equal the process context byte sum.")
        requireTrace(trace.metrics.syscallCount == trace.processes.sumOf { it.syscallCount }, "metrics.syscallCount must equal the process syscall sum.")
        requireTrace(
            trace.metrics.processDurationMillis == trace.processes.sumOf { it.durationMillis },
            "metrics.processDurationMillis must equal the process duration sum.",
        )
        requireTrace(trace.metrics.wallDurationMillis >= 0, "metrics.wallDurationMillis must be non-negative.")

        val pids = mutableSetOf<Long>()
        val agentByPid = mutableMapOf<Long, String>()
        trace.processes.forEachIndexed { index, process ->
            requireTrace(process.pid > 0, "processes[$index].pid must be positive.")
            requireTrace(pids.add(process.pid), "processes[$index].pid duplicates pid ${process.pid}.")
            agentByPid[process.pid] = process.agent
            requireTrace(process.agent.isNotBlank(), "processes[$index].agent must not be blank.")
            requireTrace(process.state.isNotBlank(), "processes[$index].state must not be blank.")
            requireTrace(process.tokens >= 0, "processes[$index].tokens must be non-negative.")
            requireTrace(process.inputTokens >= 0, "processes[$index].inputTokens must be non-negative.")
            requireTrace(process.outputTokens >= 0, "processes[$index].outputTokens must be non-negative.")
            requireTrace(process.tokens == process.inputTokens + process.outputTokens, "processes[$index].tokens must equal inputTokens + outputTokens.")
            requireTrace(process.contextBytes >= 0, "processes[$index].contextBytes must be non-negative.")
            requireTrace(process.syscallCount >= 0, "processes[$index].syscallCount must be non-negative.")
            requireTrace(process.durationMillis >= 0, "processes[$index].durationMillis must be non-negative.")
        }

        val processPids = trace.processes.map { it.pid }
        requireTrace(processPids == processPids.sorted(), "processes must be ordered by pid.")
        val expectedPath = trace.processes.map { process -> "${process.agent}(pid=${process.pid})" }
        requireTrace(trace.path == expectedPath, "path must match processes ordered by pid.")

        val expectedEventCounts = trace.events.groupingBy { event -> event.type }.eachCount().toSortedMap()
        requireTrace(trace.eventCounts == expectedEventCounts, "eventCounts must match events grouped by type.")
        trace.events.forEachIndexed { index, event ->
            requireTrace(event.timestamp.isNotBlank(), "events[$index].timestamp must not be blank.")
            requireTrace(runCatching { Instant.parse(event.timestamp) }.isSuccess, "events[$index].timestamp must be ISO-8601.")
            requireTrace(event.pid in pids, "events[$index].pid must reference a process pid.")
            agentByPid[event.pid]?.let { agent ->
                requireTrace(event.agent == agent, "events[$index].agent must match the process agent for pid ${event.pid}.")
            }
            requireTrace(event.type.isNotBlank(), "events[$index].type must not be blank.")
            requireTrace(event.message.isNotBlank(), "events[$index].message must not be blank.")
        }

        return issues
    }

    private fun buildProcessTrace(snapshot: StoredRunSnapshot): ProcessTrace {
        val processes = snapshot.processes.sortedBy { it.pid }
        val events = snapshot.events
        val eventInstants = events.mapNotNull { event ->
            runCatching { Instant.parse(event.timestamp) }.getOrNull()
        }
        val firstEventAt = eventInstants.minOrNull()
        val lastEventAt = eventInstants.maxOrNull()
        val wallDurationMillis = if (firstEventAt != null && lastEventAt != null) {
            Duration.between(firstEventAt, lastEventAt).toMillis().coerceAtLeast(0)
        } else {
            processes.sumOf { it.durationMillis }
        }

        return ProcessTrace(
            schema = PROCESS_TRACE_SCHEMA,
            runId = snapshot.runId,
            workflowName = snapshot.workflowName,
            task = snapshot.task,
            success = snapshot.success,
            metrics = ProcessTraceMetrics(
                processCount = processes.size,
                tokenTotal = processes.sumOf { it.tokens },
                inputTokens = processes.sumOf { it.inputTokens },
                outputTokens = processes.sumOf { it.outputTokens },
                contextBytes = processes.sumOf { it.contextSize },
                syscallCount = processes.sumOf { it.syscallCount },
                processDurationMillis = processes.sumOf { it.durationMillis },
                wallDurationMillis = wallDurationMillis,
                eventCount = events.size,
            ),
            path = processes.map { process -> "${process.agent}(pid=${process.pid})" },
            processes = processes.map { process ->
                ProcessTraceProcess(
                    pid = process.pid,
                    agent = process.agent,
                    state = process.state,
                    tokens = process.tokens,
                    inputTokens = process.inputTokens,
                    outputTokens = process.outputTokens,
                    contextBytes = process.contextSize,
                    syscallCount = process.syscallCount,
                    durationMillis = process.durationMillis,
                    failure = process.failure,
                )
            },
            eventCounts = events
                .groupingBy { event -> event.type }
                .eachCount()
                .toSortedMap(),
            events = events.map { event ->
                ProcessTraceEvent(
                    timestamp = event.timestamp,
                    pid = event.pid,
                    agent = event.agent,
                    type = event.type,
                    message = event.message,
                )
            },
        )
    }

    private fun renderProcessTrace(trace: ProcessTrace): String = buildString {
        appendLine("KAI PROCESS TRACE")
        appendLine("schema: ${trace.schema}")
        appendLine("run: ${trace.runId}")
        appendLine("workflow: ${trace.workflowName}")
        appendLine("success: ${trace.success}")
        appendLine("task: ${singleLine(trace.task)}")
        appendLine()
        appendLine("metrics:")
        appendLine("  processes: ${trace.metrics.processCount}")
        appendLine("  tokens: ${trace.metrics.tokenTotal} (input=${trace.metrics.inputTokens}, output=${trace.metrics.outputTokens})")
        appendLine("  context: ${trace.metrics.contextBytes}b")
        appendLine("  syscalls: ${trace.metrics.syscallCount}")
        appendLine("  process_duration: ${trace.metrics.processDurationMillis}ms")
        appendLine("  wall_duration: ${trace.metrics.wallDurationMillis}ms")
        appendLine("  events: ${trace.metrics.eventCount}")
        appendLine()
        appendLine("path:")
        if (trace.path.isEmpty()) {
            appendLine("  <none>")
        } else {
            appendLine("  <input> -> ${trace.path.joinToString(" -> ")}")
        }
        appendLine()
        appendLine("processes:")
        appendLine(formatTraceProcessHeader())
        trace.processes.forEach { process -> appendLine(formatTraceProcess(process)) }
        appendLine()
        appendLine("event_counts:")
        if (trace.eventCounts.isEmpty()) {
            appendLine("  -")
        } else {
            trace.eventCounts.forEach { (type, count) -> appendLine("  $type: $count") }
        }
        appendLine()
        appendLine("timeline:")
        if (trace.events.isEmpty()) {
            appendLine("  -")
        } else {
            trace.events.forEach { event ->
                appendLine("  ${event.timestamp} pid=${event.pid} agent=${event.agent} ${event.type} ${event.message}")
            }
        }
    }.trimEnd()

    private fun printUsage(out: PrintStream) {
        out.println(
            """
            KAI OS - AI Agent Operating System in Kotlin

            Quick start (3 steps):
              kaios demo
              kaios setup --ci
              kaios verify

            Command groups:
              Setup:
                kaios setup [--ci]
                kaios verify [--config kaios.json]
                kaios init [--template default|research|code-review|release] [--ci]

              Runtime:
                kaios demo
                kaios run "task"
                kaios run --index . --context README.md --out artifact.md --trace-out trace.json --force "task"

              Workspace:
                kaios analyze [path ...] [--format markdown|json] [--out analysis.md]
                kaios index [path ...]
                kaios context [path ...]

              Project config:
                kaios config validate [--config kaios.json]
                kaios config show [--config kaios.json]
                kaios config templates

              Observability:
                kaios runs
                kaios ps latest
                kaios inspect latest
                kaios trace latest [--format text|json] [--out trace.json] [--check]
                kaios capsule latest [--json] [--out capsule.json] [--check]
                kaios report latest
                kaios export latest [--out artifact.md]
                kaios doctor
                kaios bug-report [--out report.md]
                kaios --version
                kaios help <command>
            """.trimIndent(),
        )
    }

    private fun firstProjectRunCommand(): String {
        val readme = preferredReadmePath()
        val context = readme?.let { " --context ${displayPath(it)}" }.orEmpty()
        return "kaios run --index .$context --out artifacts/project.md --force \"summarize this project\""
    }

    private fun preferredReadmePath(): Path? =
        listOf("README.md", "README.markdown", "README")
            .map { workingDir.resolve(it).normalize() }
            .firstOrNull { it.exists() }

    private fun formatProcessHeader(): String =
        listOf("PID", "AGENT", "STATE", "TOKENS", "MEMORY", "SYSCALLS", "DURATION").joinToString("  ") {
            it.padEnd(columnWidth(it))
        }

    private fun formatProcess(process: StoredProcess): String =
        listOf(
            process.pid.toString(),
            process.agent,
            process.state,
            process.tokens.toString(),
            "${process.contextSize}b",
            process.syscallCount.toString(),
            "${process.durationMillis}ms",
        ).mapIndexed { index, value -> value.padEnd(columnWidth(index)) }
            .joinToString("  ")

    private fun columnWidth(header: String): Int = columnWidth(headerIndex(header))

    private fun headerIndex(header: String): Int = when (header) {
        "PID" -> 0
        "AGENT" -> 1
        "STATE" -> 2
        "TOKENS" -> 3
        "MEMORY" -> 4
        "SYSCALLS" -> 5
        else -> 6
    }

    private fun formatTraceProcessHeader(): String =
        listOf("PID", "AGENT", "STATE", "TOKENS", "IN", "OUT", "MEMORY", "SYSCALLS", "DURATION").joinToString("  ") {
            it.padEnd(traceColumnWidth(it))
        }

    private fun formatTraceProcess(process: ProcessTraceProcess): String =
        listOf(
            process.pid.toString(),
            process.agent,
            process.state,
            process.tokens.toString(),
            process.inputTokens.toString(),
            process.outputTokens.toString(),
            "${process.contextBytes}b",
            process.syscallCount.toString(),
            "${process.durationMillis}ms",
        ).mapIndexed { index, value -> value.padEnd(traceColumnWidth(index)) }
            .joinToString("  ")

    private fun traceColumnWidth(header: String): Int = traceColumnWidth(
        when (header) {
            "PID" -> 0
            "AGENT" -> 1
            "STATE" -> 2
            "TOKENS" -> 3
            "IN" -> 4
            "OUT" -> 5
            "MEMORY" -> 6
            "SYSCALLS" -> 7
            else -> 8
        },
    )

    private fun traceColumnWidth(index: Int): Int = when (index) {
        0 -> 6
        1 -> 12
        2 -> 10
        3 -> 8
        4 -> 6
        5 -> 6
        6 -> 8
        7 -> 8
        else -> 10
    }

    private fun columnWidth(index: Int): Int = when (index) {
        0 -> 6
        1 -> 12
        2 -> 10
        3 -> 8
        4 -> 8
        5 -> 8
        else -> 10
    }

    private fun formatContextHeader(sources: List<ContextSource>): String =
        listOf(
            "PATH".padEnd(contextPathWidth(sources)),
            "CHARS".padEnd(8),
            "ORIGINAL".padEnd(10),
            "STATUS",
        ).joinToString("  ")

    private fun formatContextSource(source: ContextSource, sources: List<ContextSource>): String =
        listOf(
            abbreviate(source.path, contextPathWidth(sources)).padEnd(contextPathWidth(sources)),
            source.content.length.toString().padEnd(8),
            source.originalChars.toString().padEnd(10),
            if (source.truncated) "truncated" else "loaded",
        ).joinToString("  ")

    private fun contextPathWidth(sources: List<ContextSource>): Int =
        (sources.maxOfOrNull { it.path.length } ?: 4).coerceAtLeast(28).coerceAtMost(72)

    private fun abbreviate(value: String, width: Int): String {
        if (value.length <= width) return value
        if (width <= 3) return value.take(width)
        return value.take(width - 3) + "..."
    }

    private fun defaultConfigPath(): Path = workingDir.resolve(KAIOS_CONFIG_FILE).normalize()

    private fun defaultCiWorkflowPath(): Path =
        workingDir.resolve(".github").resolve("workflows").resolve("kaios.yml").normalize()

    private fun toolRegistry() =
        builtInToolRegistry(
            fileRoot = workingDir.resolve(".kaios").resolve("files"),
            httpAllowlist = httpAllowlist(),
        )

    private fun httpAllowlist(): List<String> =
        env("KAIOS_HTTP_ALLOWLIST")
            ?.split(',', ';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

    private fun contextLoader() =
        ContextLoader(
            workingDir = workingDir,
            maxChars = env("KAIOS_CONTEXT_MAX_CHARS")?.toIntOrNull()?.coerceAtLeast(1) ?: 80_000,
        )

    private fun workspaceIndexer() =
        WorkspaceIndexer(
            workingDir = workingDir,
            maxFiles = env("KAIOS_INDEX_MAX_FILES")?.toIntOrNull()?.coerceAtLeast(1) ?: 500,
        )

    private fun resolvePath(value: String): Path {
        val path = Paths.get(value)
        return if (path.isAbsolute) path.normalize() else workingDir.resolve(path).normalize()
    }

    private fun defaultArtifactPath(runId: RunId): Path =
        artifactRoot.resolve("${runId.value}.md").normalize()

    private fun defaultTracePath(runId: RunId): Path =
        artifactRoot.resolve("${runId.value}.trace.json").normalize()

    private fun defaultCapsulePath(runId: RunId): Path =
        capsuleRoot.resolve("${runId.value}.capsule.json").normalize()

    private fun writeArtifact(snapshot: StoredRunSnapshot, path: Path, force: Boolean): Path {
        if (path.exists() && !force) {
            error("Artifact '$path' already exists. Use --force to overwrite it.")
        }
        path.parent?.let { Files.createDirectories(it) }
        path.writeText(artifactExporter.render(snapshot))
        return path
    }

    private fun writeTextOutput(text: String, path: Path, force: Boolean): Path {
        if (path.exists() && !force) {
            error("Output '$path' already exists. Use --force to overwrite it.")
        }
        path.parent?.let { Files.createDirectories(it) }
        path.writeText(text)
        return path
    }

    private fun writeTraceJson(snapshot: StoredRunSnapshot, path: Path, force: Boolean): Path =
        writeTextOutput("${TRACE_JSON.encodeToString(buildProcessTrace(snapshot))}\n", path, force)

    private fun composeRunInput(task: String, context: ContextBundle, workspaceIndex: WorkspaceIndex): String {
        if (context.sources.isEmpty() && workspaceIndex.files.isEmpty()) return task

        return buildString {
            append(task.trim())
            if (workspaceIndex.files.isNotEmpty()) {
                appendLine()
                appendLine()
                appendLine("[KAIOS_WORKSPACE_INDEX]")
                appendLine(workspaceIndex.promptBlock())
                appendLine("[/KAIOS_WORKSPACE_INDEX]")
            }
            if (context.sources.isNotEmpty()) {
                appendLine()
                appendLine()
                appendLine("[KAIOS_CONTEXT]")
                context.sources.forEach { source ->
                    appendLine("### ${source.path}")
                    appendLine("```")
                    appendLine(source.content.trimEnd())
                    appendLine("```")
                }
                if (context.truncated) {
                    appendLine("Context was truncated at ${context.maxChars} characters.")
                }
                appendLine("[/KAIOS_CONTEXT]")
            }
        }
    }

    private fun composeTaskSummary(task: String, context: ContextBundle, workspaceIndex: WorkspaceIndex): String {
        if (context.sources.isEmpty() && workspaceIndex.files.isEmpty()) return task

        return buildString {
            appendLine(task)
            if (workspaceIndex.files.isNotEmpty()) {
                appendLine()
                appendLine("Workspace Index:")
                appendLine("- ${workspaceIndex.summary()}")
                workspaceIndex.notableFiles.take(10).forEach { file ->
                    appendLine("- ${file.path} (${file.language}, ${file.lines} lines)")
                }
            }
            if (context.sources.isNotEmpty()) {
                appendLine()
                appendLine("Context:")
                context.sources.forEach { source ->
                    val suffix = if (source.truncated) ", truncated from ${source.originalChars}" else ""
                    appendLine("- ${source.path} (${source.content.length} chars$suffix)")
                }
                if (context.truncated) {
                    appendLine("- total context truncated at ${context.maxChars} chars")
                }
            }
        }.trimEnd()
    }

    private fun displayPath(path: Path): String =
        if (path.startsWith(workingDir)) {
            workingDir.relativize(path).toString()
        } else {
            path.toString()
        }

    private fun ciDisplayPath(path: Path): String =
        displayPath(path).replace('\\', '/')

    private fun shellQuote(value: String): String =
        "'${value.replace("'", "'\"'\"'")}'"

    private fun projectCiWorkflowText(configPath: Path): String {
        val config = shellQuote(ciDisplayPath(configPath))
        return """
            name: KAI OS Agent Gate

            on:
              pull_request:
              push:
                branches: [main]

            permissions:
              contents: read

            jobs:
              kaios:
                name: Validate agent workflow
                runs-on: ubuntu-latest
                env:
                  KAIOS_VERSION: "$KAIOS_VERSION"
                  KAIOS_MODEL_PROVIDER: mock
                steps:
                  - name: Checkout
                    uses: actions/checkout@v6.0.3

                  - name: Set up Java
                    uses: actions/setup-java@v5.2.0
                    with:
                      distribution: temurin
                      java-version: "17"

                  - name: Install KAI OS
                    run: |
                      curl -fsSL https://morning-verlu.github.io/KAI/install.sh | sh
                      echo "${'$'}HOME/.kaios/bin" >> "${'$'}GITHUB_PATH"

                  - name: Verify KAI OS project
                    run: kaios verify --config $config
        """.trimIndent() + "\n"
    }

    private fun parseRunCommand(args: List<String>): RunCommand {
        var configPath: Path? = null
        var useBuiltInDefault = false
        var outputPath: Path? = null
        var traceOutputPath: Path? = null
        var forceOutput = false
        val contextPaths = mutableListOf<Path>()
        val indexPaths = mutableListOf<Path>()
        val taskParts = mutableListOf<String>()
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                arg == "--default" -> {
                    useBuiltInDefault = true
                    index += 1
                }
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--trace-out" || arg == "--trace-output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    traceOutputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--trace-out=") || arg.startsWith("--trace-output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    traceOutputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force-output" || arg == "--force" || arg == "-f" -> {
                    forceOutput = true
                    index += 1
                }
                arg == "--context" || arg == "--ctx" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    contextPaths.add(resolvePath(value))
                    index += 2
                }
                arg.startsWith("--context=") || arg.startsWith("--ctx=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    contextPaths.add(resolvePath(value))
                    index += 1
                }
                arg == "--index" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    indexPaths.add(resolvePath(value))
                    index += 2
                }
                arg.startsWith("--index=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--index requires a path." }
                    indexPaths.add(resolvePath(value))
                    index += 1
                }
                arg == "--" -> {
                    taskParts += args.drop(index + 1)
                    index = args.size
                }
                arg.startsWith("-") && taskParts.isEmpty() -> {
                    error("Unknown run option '$arg'. Use -- before a task that starts with '-'.")
                }
                else -> {
                    taskParts += arg
                    index += 1
                }
            }
        }

        val task = taskParts.joinToString(" ").trim()
        require(task.isNotBlank()) { "Task cannot be blank." }
        require(!(configPath != null && useBuiltInDefault)) { "Use either --config or --default, not both." }
        return RunCommand(task, configPath, useBuiltInDefault, outputPath, traceOutputPath, forceOutput, contextPaths, indexPaths)
    }

    private fun parseContextCommand(args: List<String>): List<Path> {
        if (args.isEmpty()) return listOf(workingDir)

        val paths = mutableListOf<Path>()
        var index = 0
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--" -> {
                    paths.addAll(args.drop(index + 1).map(::resolvePath))
                    index = args.size
                }
                arg.startsWith("-") -> error("Unknown context option '$arg'.")
                else -> {
                    paths.add(resolvePath(arg))
                    index += 1
                }
            }
        }

        return paths.ifEmpty { listOf(workingDir) }
    }

    private fun parseIndexCommand(args: List<String>): List<Path> {
        if (args.isEmpty()) return listOf(workingDir)

        val paths = mutableListOf<Path>()
        var index = 0
        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--" -> {
                    paths.addAll(args.drop(index + 1).map(::resolvePath))
                    index = args.size
                }
                arg.startsWith("-") -> error("Unknown index option '$arg'.")
                else -> {
                    paths.add(resolvePath(arg))
                    index += 1
                }
            }
        }

        return paths.ifEmpty { listOf(workingDir) }
    }

    private fun parseAnalyzeCommand(args: List<String>): AnalyzeCommand {
        var outputPath: Path? = null
        var force = false
        var format = AnalyzeFormat.Markdown
        val paths = mutableListOf<Path>()
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                arg == "--json" -> {
                    format = AnalyzeFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires markdown or json.")
                    format = parseAnalyzeFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires markdown or json." }
                    format = parseAnalyzeFormat(value)
                    index += 1
                }
                arg == "--" -> {
                    paths.addAll(args.drop(index + 1).map(::resolvePath))
                    index = args.size
                }
                arg.startsWith("-") -> error("Unknown analyze option '$arg'.")
                else -> {
                    paths.add(resolvePath(arg))
                    index += 1
                }
            }
        }

        return AnalyzeCommand(paths.ifEmpty { listOf(workingDir) }, outputPath, force, format)
    }

    private fun parseAnalyzeFormat(value: String): AnalyzeFormat =
        when (value.lowercase().trim()) {
            "markdown", "md" -> AnalyzeFormat.Markdown
            "json" -> AnalyzeFormat.Json
            else -> error("Unknown analyze format '$value'. Use markdown or json.")
        }

    private fun parseSetupCommand(args: List<String>): SetupCommand {
        var configPath = defaultConfigPath()
        var force = false
        var templateId = "research"
        var writeCi = false
        var format = SetupFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                arg == "--ci" -> {
                    writeCi = true
                    index += 1
                }
                arg == "--json" -> {
                    format = SetupFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseSetupFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseSetupFormat(value)
                    index += 1
                }
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                arg == "--template" || arg == "-t" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a template id.")
                    requireProjectTemplate(value)
                    templateId = value.lowercase().trim()
                    index += 2
                }
                arg.startsWith("--template=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--template requires a template id." }
                    requireProjectTemplate(value)
                    templateId = value.lowercase().trim()
                    index += 1
                }
                else -> error("Unknown setup option '$arg'.")
            }
        }

        return SetupCommand(configPath, force, templateId, writeCi, format)
    }

    private fun parseSetupFormat(value: String): SetupFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> SetupFormat.Text
            "json" -> SetupFormat.Json
            else -> error("Unknown setup format '$value'. Use text or json.")
        }

    private fun parseVerifyCommand(args: List<String>): VerifyCommand {
        var configPath = defaultConfigPath()
        var format = VerifyFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                arg == "--json" -> {
                    format = VerifyFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseVerifyFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseVerifyFormat(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown verify option '$arg'.")
                else -> error("Unexpected verify argument '$arg'.")
            }
        }

        return VerifyCommand(configPath, format)
    }

    private fun parseVerifyFormat(value: String): VerifyFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> VerifyFormat.Text
            "json" -> VerifyFormat.Json
            else -> error("Unknown verify format '$value'. Use text or json.")
        }

    private fun parseInitCommand(args: List<String>): InitCommand {
        var configPath = defaultConfigPath()
        var force = false
        var templateId = "default"
        var listTemplates = false
        var writeCi = false
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--list-templates" -> {
                    listTemplates = true
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                arg == "--ci" -> {
                    writeCi = true
                    index += 1
                }
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                arg == "--template" || arg == "-t" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a template id.")
                    requireProjectTemplate(value)
                    templateId = value.lowercase().trim()
                    index += 2
                }
                arg.startsWith("--template=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--template requires a template id." }
                    requireProjectTemplate(value)
                    templateId = value.lowercase().trim()
                    index += 1
                }
                else -> error("Unknown init option '$arg'.")
            }
        }

        return InitCommand(configPath, force, templateId, listTemplates, writeCi)
    }

    private fun parseConfigPath(args: List<String>): Path {
        var configPath = defaultConfigPath()
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                else -> error("Unknown config option '$arg'.")
            }
        }

        return configPath
    }

    private fun parseConfigValidateCommand(args: List<String>): ConfigValidationCommand {
        var configPath = defaultConfigPath()
        var format = ConfigValidationFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    configPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--config=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--config requires a path." }
                    configPath = resolvePath(value)
                    index += 1
                }
                arg == "--json" -> {
                    format = ConfigValidationFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseConfigValidationFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseConfigValidationFormat(value)
                    index += 1
                }
                else -> error("Unknown config option '$arg'.")
            }
        }

        return ConfigValidationCommand(configPath, format)
    }

    private fun parseConfigValidationFormat(value: String): ConfigValidationFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> ConfigValidationFormat.Text
            "json" -> ConfigValidationFormat.Json
            else -> error("Unknown config validation format '$value'. Use text or json.")
        }

    private fun parseExportCommand(args: List<String>): ExportCommand {
        val runIdText = args.firstOrNull() ?: error("Run id is required.")
        var outputPath: Path? = null
        var force = false
        var index = 1

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                else -> error("Unknown export option '$arg'.")
            }
        }

        return ExportCommand(runIdText, outputPath, force)
    }

    private fun parseTraceCommand(args: List<String>): TraceCommand {
        var runIdText: String? = null
        var format = TraceFormat.Text
        var outputPath: Path? = null
        var forceOutput = false
        var check = false
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = TraceFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseTraceFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseTraceFormat(value)
                    index += 1
                }
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    forceOutput = true
                    index += 1
                }
                arg == "--check" -> {
                    check = true
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown trace option '$arg'.")
                runIdText == null -> {
                    runIdText = arg
                    index += 1
                }
                else -> error("Unexpected trace argument '$arg'.")
            }
        }

        if (check && outputPath != null) {
            error("--check does not write output; omit --out.")
        }
        if (check && forceOutput) {
            error("--check does not write output; omit --force.")
        }

        return TraceCommand(runIdText ?: error("Run id is required."), format, outputPath, forceOutput, check)
    }

    private fun parseTraceFormat(value: String): TraceFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> TraceFormat.Text
            "json" -> TraceFormat.Json
            else -> error("Unknown trace format '$value'. Use text or json.")
        }

    private fun parseCapsuleCommand(args: List<String>): CapsuleCommand {
        var runIdText: String? = null
        var inputPath: Path? = null
        var outputPath: Path? = null
        var forceOutput = false
        var printJson = false
        var check = false
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    printJson = true
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires json.")
                    parseCapsuleFormat(value)
                    printJson = true
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires json." }
                    parseCapsuleFormat(value)
                    printJson = true
                    index += 1
                }
                arg == "--file" || arg == "--from" || arg == "--input" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    inputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--file=") || arg.startsWith("--from=") || arg.startsWith("--input=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    inputPath = resolvePath(value)
                    index += 1
                }
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    forceOutput = true
                    index += 1
                }
                arg == "--check" -> {
                    check = true
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown capsule option '$arg'.")
                runIdText == null -> {
                    runIdText = arg
                    index += 1
                }
                else -> error("Unexpected capsule argument '$arg'.")
            }
        }

        if (check && outputPath != null) {
            error("--check does not write output; omit --out.")
        }
        if (check && forceOutput) {
            error("--check does not write output; omit --force.")
        }
        if (check && printJson) {
            error("--check prints status text; omit --json.")
        }
        if (runIdText != null && inputPath != null) {
            error("Use either a run id or --file, not both.")
        }

        return CapsuleCommand(
            runIdText = runIdText,
            inputPath = inputPath,
            outputPath = outputPath,
            forceOutput = forceOutput,
            printJson = printJson,
            check = check,
        )
    }

    private fun parseCapsuleFormat(value: String) {
        when (value.lowercase().trim()) {
            "json" -> Unit
            else -> error("Unknown capsule format '$value'. Use json.")
        }
    }

    private fun parseDoctorCommand(args: List<String>): DoctorCommand {
        var format = DoctorFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = DoctorFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseDoctorFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseDoctorFormat(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown doctor option '$arg'.")
                else -> error("Unexpected doctor argument '$arg'.")
            }
        }

        return DoctorCommand(format)
    }

    private fun parseDoctorFormat(value: String): DoctorFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> DoctorFormat.Text
            "json" -> DoctorFormat.Json
            else -> error("Unknown doctor format '$value'. Use text or json.")
        }

    private fun parseBugReportCommand(args: List<String>): BugReportCommand {
        var outputPath: Path? = null
        var force = false
        var format = BugReportFormat.Markdown
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = BugReportFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires markdown or json.")
                    format = parseBugReportFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires markdown or json." }
                    format = parseBugReportFormat(value)
                    index += 1
                }
                arg == "--out" || arg == "--output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    outputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--out=") || arg.startsWith("--output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    outputPath = resolvePath(value)
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown bug-report option '$arg'.")
                else -> error("Unexpected bug-report argument '$arg'.")
            }
        }

        return BugReportCommand(outputPath, force, format)
    }

    private fun parseBugReportFormat(value: String): BugReportFormat =
        when (value.lowercase().trim()) {
            "markdown", "md", "text", "plain" -> BugReportFormat.Markdown
            "json" -> BugReportFormat.Json
            else -> error("Unknown bug-report format '$value'. Use markdown or json.")
        }

    private fun parseRunsCommand(args: List<String>): RunsCommand {
        var format = RunsFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = RunsFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseRunsFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseRunsFormat(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown runs option '$arg'.")
                else -> error("Unexpected runs argument '$arg'.")
            }
        }

        return RunsCommand(format)
    }

    private fun parseRunsFormat(value: String): RunsFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> RunsFormat.Text
            "json" -> RunsFormat.Json
            else -> error("Unknown runs format '$value'. Use text or json.")
        }

    private fun printTemplates(out: PrintStream) {
        out.println("TEMPLATES")
        projectConfigTemplates.forEach { template ->
            out.println("${template.id.padEnd(12)} ${template.description}")
        }
    }
}

private data class CommandHelp(
    val usage: String,
    val summary: String,
    val examples: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

private data class RunCommand(
    val task: String,
    val configPath: Path?,
    val useBuiltInDefault: Boolean,
    val outputPath: Path?,
    val traceOutputPath: Path?,
    val forceOutput: Boolean,
    val contextPaths: List<Path>,
    val indexPaths: List<Path>,
)

private data class SetupCommand(
    val configPath: Path,
    val force: Boolean,
    val templateId: String,
    val writeCi: Boolean,
    val format: SetupFormat,
)

private enum class SetupFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private enum class SetupFileAction(val id: String) {
    Created("created"),
    Existing("existing"),
    Overwritten("overwritten"),
    Skipped("skipped"),
}

@Serializable
private data class SetupReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val requestedTemplate: String,
    val config: SetupFileReport,
    val ci: SetupFileReport,
    val doctor: DoctorReport,
    val validation: ConfigValidationReport,
    val next: List<String>,
)

@Serializable
private data class SetupFileReport(
    val path: String?,
    val action: String,
)

private data class VerifyCommand(
    val configPath: Path,
    val format: VerifyFormat,
)

private enum class VerifyFormat(val id: String) {
    Text("text"),
    Json("json"),
}

@Serializable
private data class VerifyReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val status: String,
    val doctor: DoctorReport,
    val config: ConfigValidationReport,
    val run: VerifyRun?,
    val trace: VerifyTrace?,
    val errors: List<String>,
    val next: List<String>,
)

@Serializable
private data class VerifyRun(
    val runId: String,
    val workflowName: String,
    val success: Boolean,
    val task: String,
    val snapshot: String,
    val processCount: Int,
    val tokenTotal: Int,
    val syscallCount: Int,
    val contextBytes: Int,
    val durationMillis: Long,
)

@Serializable
private data class VerifyTrace(
    val runId: String,
    val schema: String,
    val valid: Boolean,
    val processCount: Int,
    val eventCount: Int,
    val issues: List<String>,
)

private data class InitCommand(
    val configPath: Path,
    val force: Boolean,
    val templateId: String,
    val listTemplates: Boolean,
    val writeCi: Boolean,
)

private data class ExportCommand(
    val runIdText: String,
    val outputPath: Path?,
    val force: Boolean,
)

private data class TraceCommand(
    val runIdText: String,
    val format: TraceFormat,
    val outputPath: Path?,
    val forceOutput: Boolean,
    val check: Boolean,
)

private data class CapsuleCommand(
    val runIdText: String?,
    val inputPath: Path?,
    val outputPath: Path?,
    val forceOutput: Boolean,
    val printJson: Boolean,
    val check: Boolean,
)

private enum class TraceFormat(val id: String) {
    Text("text"),
    Json("json"),
}

@Serializable
private data class RunCapsule(
    val schema: String,
    val version: String,
    val generatedAt: String,
    val cwd: String,
    val run: RunCapsuleRun,
    val provenance: RunCapsuleProvenance,
    val replay: RunCapsuleReplay,
    val validation: RunCapsuleValidation,
    val snapshot: StoredRunSnapshot,
    val trace: ProcessTrace,
)

@Serializable
private data class RunCapsuleRun(
    val runId: String,
    val workflowName: String,
    val success: Boolean,
    val task: String,
    val processCount: Int,
    val tokenTotal: Int,
    val syscallCount: Int,
    val contextBytes: Int,
    val durationMillis: Long,
)

@Serializable
private data class RunCapsuleProvenance(
    val snapshotPath: String,
    val snapshotSha256: String,
    val embeddedSnapshotSha256: String? = null,
    val traceSha256: String,
    val configPath: String,
    val configSha256: String?,
    val configValid: Boolean,
    val configWorkflowName: String?,
    val configAgentIds: List<String>,
)

@Serializable
private data class RunCapsuleReplay(
    val commands: List<String>,
    val note: String,
)

@Serializable
private data class RunCapsuleValidation(
    val valid: Boolean,
    val issues: List<String>,
    val checkedAt: String,
)

@Serializable
private data class ProcessTrace(
    val schema: String,
    val runId: String,
    val workflowName: String,
    val task: String,
    val success: Boolean,
    val metrics: ProcessTraceMetrics,
    val path: List<String>,
    val processes: List<ProcessTraceProcess>,
    val eventCounts: Map<String, Int>,
    val events: List<ProcessTraceEvent>,
)

@Serializable
private data class ProcessTraceMetrics(
    val processCount: Int,
    val tokenTotal: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextBytes: Int,
    val syscallCount: Int,
    val processDurationMillis: Long,
    val wallDurationMillis: Long,
    val eventCount: Int,
)

@Serializable
private data class ProcessTraceProcess(
    val pid: Long,
    val agent: String,
    val state: String,
    val tokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextBytes: Int,
    val syscallCount: Int,
    val durationMillis: Long,
    val failure: String? = null,
)

@Serializable
private data class ProcessTraceEvent(
    val timestamp: String,
    val pid: Long,
    val agent: String,
    val type: String,
    val message: String,
)

private data class AnalyzeCommand(
    val paths: List<Path>,
    val outputPath: Path?,
    val force: Boolean,
    val format: AnalyzeFormat,
)

private enum class AnalyzeFormat(val id: String) {
    Markdown("markdown"),
    Json("json"),
}

private data class ConfigValidationCommand(
    val configPath: Path,
    val format: ConfigValidationFormat,
)

private enum class ConfigValidationFormat(val id: String) {
    Text("text"),
    Json("json"),
}

@Serializable
private data class ConfigValidationReport(
    val schema: String,
    val config: String,
    val valid: Boolean,
    val workflowName: String?,
    val agentCount: Int,
    val agentIds: List<String>,
    val errors: List<String>,
)

private data class DoctorCommand(
    val format: DoctorFormat,
)

private enum class DoctorFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private data class BugReportCommand(
    val outputPath: Path?,
    val force: Boolean,
    val format: BugReportFormat,
)

private enum class BugReportFormat(val id: String) {
    Markdown("markdown"),
    Json("json"),
}

@Serializable
private data class DoctorReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val summary: DoctorSummary,
    val checks: List<DoctorReportCheck>,
    val next: List<String>,
)

@Serializable
private data class DoctorSummary(
    val status: String,
    val failed: Int,
    val warnings: Int,
)

@Serializable
private data class DoctorReportCheck(
    val name: String,
    val status: String,
    val detail: String,
)

@Serializable
private data class BugReport(
    val schema: String,
    val version: String,
    val generatedAt: String,
    val cwd: String,
    val files: BugReportFiles,
    val doctor: DoctorReport,
    val config: ConfigValidationReport,
    val latestRun: BugReportRun?,
    val trace: BugReportTrace?,
    val next: List<String>,
)

@Serializable
private data class BugReportFiles(
    val config: String,
    val runsDir: String,
    val reportsDir: String,
    val artifactsDir: String,
)

@Serializable
private data class BugReportRun(
    val runId: String,
    val workflowName: String,
    val success: Boolean,
    val task: String,
    val processCount: Int,
    val tokenTotal: Int,
    val syscallCount: Int,
    val contextBytes: Int,
    val durationMillis: Long,
)

@Serializable
private data class BugReportTrace(
    val runId: String,
    val schema: String,
    val valid: Boolean,
    val processCount: Int,
    val eventCount: Int,
    val issues: List<String>,
)

private data class RunsCommand(
    val format: RunsFormat,
)

private enum class RunsFormat(val id: String) {
    Text("text"),
    Json("json"),
}

@Serializable
private data class RunsReport(
    val schema: String,
    val count: Int,
    val latestRunId: String?,
    val runs: List<RunsReportItem>,
)

@Serializable
private data class RunsReportItem(
    val runId: String,
    val alias: String?,
    val status: String,
    val success: Boolean,
    val workflowName: String,
    val task: String,
    val processCount: Int,
    val tokenTotal: Int,
    val syscallCount: Int,
    val contextBytes: Int,
    val durationMillis: Long,
)

private enum class DoctorStatus {
    OK,
    WARN,
    FAIL,
}

private data class DoctorCheck(
    val name: String,
    val status: DoctorStatus,
    val detail: String,
)

private fun javaMajorVersion(version: String): Int? {
    val first = version.substringBefore(".").toIntOrNull() ?: return null
    if (first != 1) return first
    return version.substringAfter(".", "").substringBefore(".").toIntOrNull()
}

fun modelProviderFromEnv(env: (String) -> String? = System::getenv): ModelProvider =
    when (val provider = env("KAIOS_MODEL_PROVIDER")?.lowercase()?.trim().orEmpty().ifBlank { "mock" }) {
        "mock" -> MockModelProvider()
        "openai", "openai-compatible" -> OpenAiCompatibleModelProvider(OpenAiCompatibleConfig.fromEnv(env))
        "ollama" -> OllamaModelProvider(OllamaConfig.fromEnv(env))
        else -> error("Unsupported KAIOS_MODEL_PROVIDER '$provider'. Use mock, openai, or ollama.")
    }

fun memoryStoreFromEnv(env: (String) -> String? = System::getenv): MemoryStore =
    when (val store = env("KAIOS_MEMORY_STORE")?.lowercase()?.trim().orEmpty().ifBlank { "session" }) {
        "session" -> SessionMemoryStore()
        "sqlite" -> SQLiteMemoryStore(env("KAIOS_SQLITE_PATH")?.let { Paths.get(it) } ?: Paths.get(".kaios", "kaios.db"))
        else -> error("Unsupported KAIOS_MEMORY_STORE '$store'. Use session or sqlite.")
    }

fun defaultWorkflow(memory: MemoryStore): Workflow {
    val planner = agent("planner") {
        instruction("Plan the task as an agent process.")
        tool("echo")
        tool("clock")
        memory(memory)
    }

    val executor = agent("executor") {
        instruction("Execute the plan through permitted syscalls.")
        tool("echo")
        tool("mock-http")
        memory(memory)
    }

    val validator = agent("validator") {
        instruction("Validate the executor output.")
        tool("echo")
        memory(memory)
    }

    return workflow("default") {
        node("planner", planner)
        node("executor", executor).dependsOn("planner")
        node("validator", validator).dependsOn("executor")
    }
}

private fun defaultSnapshotRoot() =
    System.getenv("KAIOS_RUNS_DIR")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(".kaios", "runs")

private fun defaultReportRoot() =
    System.getenv("KAIOS_REPORTS_DIR")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(".kaios", "reports")

private fun defaultArtifactRoot() =
    System.getenv("KAIOS_ARTIFACTS_DIR")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(".kaios", "artifacts")

private fun defaultCapsuleRoot() =
    System.getenv("KAIOS_CAPSULES_DIR")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
        ?: Paths.get(".kaios", "capsules")

private fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
