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
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val KAIOS_VERSION = "0.1.10"

fun main(args: Array<String>) {
    val exitCode = KaiosCli().run(args, System.out, System.err)
    if (exitCode != 0) exitProcess(exitCode)
}

class KaiosCli(
    private val snapshotStore: FileRunSnapshotStore = FileRunSnapshotStore(defaultSnapshotRoot()),
    private val reportRoot: Path = defaultReportRoot(),
    private val reportRenderer: ProcessReportRenderer = ProcessReportRenderer(),
    private val artifactRoot: Path = defaultArtifactRoot(),
    private val artifactExporter: ArtifactExporter = ArtifactExporter(),
    private val snapshotRoot: Path = defaultSnapshotRoot(),
    private val workingDir: Path = Paths.get("").toAbsolutePath().normalize(),
    private val env: (String) -> String? = System::getenv,
) {
    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isEmpty()) {
            printUsage(err)
            return 1
        }

        return when (args.first()) {
            "init" -> initProject(args.drop(1), out, err)
            "run" -> runWorkflow(args.drop(1), out, err)
            "config" -> configCommand(args.drop(1), out, err)
            "runs" -> listRuns(out)
            "ps" -> printProcessTable(args.drop(1), out, err)
            "inspect" -> inspectRun(args.drop(1), out, err)
            "report" -> generateReport(args.drop(1), out, err)
            "export" -> exportRun(args.drop(1), out, err)
            "doctor" -> doctor(out)
            "help", "--help", "-h" -> {
                printUsage(out)
                0
            }
            else -> {
                err.println("Unknown command '${args.first()}'.")
                printUsage(err)
                1
            }
        }
    }

    private fun runWorkflow(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseRunCommand(args) }.getOrElse { error ->
            err.println(error.message)
            err.println("Usage: kaios run [--context path] [--config kaios.json] [--out artifact.md] \"task\"")
            return 1
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

        val result = scheduler.run(workflow, context.inputFor(command.task))
        val path = snapshotStore.save(context.taskSummary(command.task), result)
        val artifactPath = runCatching {
            command.outputPath?.let { outputPath ->
                val snapshot = snapshotStore.load(result.runId)
                writeArtifact(snapshot, outputPath, command.forceOutput)
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
        artifactPath?.let { out.println("artifact: $it") }
        out.println()
        out.println(result.finalOutput)
        out.println()
        out.println("next:")
        out.println("  kaios ps ${result.runId.value}")
        out.println("  kaios inspect ${result.runId.value}")
        out.println("  kaios report ${result.runId.value}")
        out.println("  kaios export ${result.runId.value}")
        return if (result.success) 0 else 2
    }

    private fun initProject(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseInitCommand(args) }.getOrElse { error ->
            err.println(error.message)
            err.println("Usage: kaios init [--template default|research|code-review|release] [--config kaios.json] [--force]")
            return 1
        }

        if (command.listTemplates) {
            printTemplates(out)
            return 0
        }

        val template = requireProjectTemplate(command.templateId)
        val path = command.configPath
        if (path.exists() && !command.force) {
            err.println("Config '$path' already exists. Use --force to overwrite it.")
            return 1
        }

        path.parent?.let { Files.createDirectories(it) }
        path.writeText(projectConfigText(command.templateId))

        out.println("created: $path")
        out.println("template: ${command.templateId}")
        out.println()
        out.println("next:")
        out.println("  kaios config show --config ${displayPath(path)}")
        out.println("  kaios run \"${template.exampleTask}\"")
        out.println("  kaios ps <run-id>")
        return 0
    }

    private fun configCommand(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val subcommand = args.firstOrNull()
        if (subcommand == null) {
            err.println("Usage: kaios config <validate|show|templates> [--config kaios.json]")
            return 1
        }

        return when (subcommand) {
            "templates" -> {
                printTemplates(out)
                0
            }
            "validate" -> validateConfig(args.drop(1), out, err)
            "show" -> showConfig(args.drop(1), out, err)
            else -> {
                err.println("Unknown config command '$subcommand'.")
                err.println("Usage: kaios config <validate|show|templates> [--config kaios.json]")
                1
            }
        }
    }

    private fun validateConfig(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val path = runCatching { parseConfigPath(args) }.getOrElse { error ->
            err.println(error.message)
            err.println("Usage: kaios config validate [--config kaios.json]")
            return 1
        }

        return runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }
            .fold(
                onSuccess = { workflow ->
                    out.println("config: $path")
                    out.println("status: valid")
                    out.println("workflow: ${workflow.name}")
                    out.println("agents: ${workflow.nodes.size}")
                    0
                },
                onFailure = { error ->
                    err.println(error.message)
                    1
                },
            )
    }

    private fun showConfig(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val path = runCatching { parseConfigPath(args) }.getOrElse { error ->
            err.println(error.message)
            err.println("Usage: kaios config show [--config kaios.json]")
            return 1
        }

        val workflow = runCatching { loadProjectWorkflow(path, SessionMemoryStore(), toolRegistry()) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("config: $path")
        out.println("workflow: ${workflow.name}")
        out.println("agents:")
        workflow.nodes.forEach { node ->
            val tools = node.agent.allowedTools.sorted().ifEmpty { listOf("-") }.joinToString(",")
            val dependencies = node.dependencies.sorted().ifEmpty { listOf("-") }.joinToString(",")
            val fallback = node.fallback?.let { " fallback=$it" }.orEmpty()
            val fallbackOnly = if (node.fallbackOnly) " fallbackOnly=true" else ""
            out.println("  ${node.id} tools=$tools dependsOn=$dependencies$fallback$fallbackOnly")
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

    private fun listRuns(out: PrintStream): Int {
        val snapshots = snapshotStore.list()
        if (snapshots.isEmpty()) {
            out.println("No run snapshots found.")
            return 0
        }

        out.println("RUNS (${snapshots.size})")
        out.println("RUN ID        STATUS     WORKFLOW      PROCS  TOKENS  TASK")
        snapshots.forEach { snapshot ->
            val status = if (snapshot.success) "success" else "failed"
            val tokens = snapshot.processes.sumOf { it.tokens }
            out.println(
                listOf(
                    snapshot.runId.padEnd(13),
                    status.padEnd(10),
                    snapshot.workflowName.padEnd(13),
                    snapshot.processes.size.toString().padEnd(6),
                    tokens.toString().padEnd(7),
                    snapshot.task,
                ).joinToString(""),
            )
        }
        return 0
    }

    private fun printProcessTable(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val runId = args.firstOrNull()?.let(::RunId)
        if (runId == null) {
            err.println("Usage: kaios ps <run-id>")
            return 1
        }

        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            err.println(it.message)
            return 1
        }

        out.println("RUN ${snapshot.runId}  workflow=${snapshot.workflowName}  success=${snapshot.success}")
        out.println(formatProcessHeader())
        snapshot.processes.forEach { out.println(formatProcess(it)) }
        return 0
    }

    private fun generateReport(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val runId = args.firstOrNull()?.let(::RunId)
        if (runId == null) {
            err.println("Usage: kaios report <run-id>")
            return 1
        }

        val snapshots = snapshotStore.list()
        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            err.println(it.message)
            return 1
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
            err.println(error.message)
            err.println("Usage: kaios export <run-id> [--out artifact.md] [--force]")
            return 1
        }

        val snapshot = runCatching { snapshotStore.load(command.runId) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        val path = runCatching { writeArtifact(snapshot, command.outputPath, command.force) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        out.println("artifact: $path")
        return 0
    }

    private fun doctor(out: PrintStream): Int {
        val checks = listOf(
            javaCheck(),
            directoryCheck("runs directory", snapshotRoot),
            directoryCheck("reports directory", reportRoot),
            directoryCheck("artifacts directory", artifactRoot),
            modelProviderCheck(),
            memoryStoreCheck(),
            configCheck(),
            snapshotsCheck(),
        )

        out.println("KAI OS doctor")
        out.println("version: $KAIOS_VERSION")
        out.println("cwd: $workingDir")
        out.println()
        checks.forEach { check ->
            out.println("[${check.status.name}] ${check.name}: ${check.detail}")
        }
        out.println()

        val failed = checks.count { it.status == DoctorStatus.FAIL }
        val warnings = checks.count { it.status == DoctorStatus.WARN }
        when {
            failed > 0 -> out.println("summary: $failed failed, $warnings warning(s)")
            warnings > 0 -> out.println("summary: ready with $warnings warning(s)")
            else -> out.println("summary: ready")
        }

        return if (failed > 0) 2 else 0
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

    private fun modelProviderCheck(): DoctorCheck {
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
                    DoctorCheck("model provider", DoctorStatus.FAIL, error.message ?: "invalid model provider configuration")
                },
            )
    }

    private fun memoryStoreCheck(): DoctorCheck {
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
                    DoctorCheck("memory store", DoctorStatus.FAIL, error.message ?: "invalid memory store configuration")
                },
            )
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

    private fun configCheck(): DoctorCheck {
        val path = defaultConfigPath()
        if (!path.exists()) {
            return DoctorCheck("project config", DoctorStatus.OK, "no $KAIOS_CONFIG_FILE found; using built-in default workflow")
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
        val runId = args.firstOrNull()?.let(::RunId)
        if (runId == null) {
            err.println("Usage: kaios inspect <run-id>")
            return 1
        }

        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            err.println(it.message)
            return 1
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

    private fun printUsage(out: PrintStream) {
        out.println(
            """
            KAI OS - AI Agent Operating System in Kotlin

            Usage:
              kaios init [--template default|research|code-review|release]
              kaios config validate [--config kaios.json]
              kaios config show [--config kaios.json]
              kaios config templates
              kaios run "task"
              kaios run --context README.md "task"
              kaios run --config kaios.json "task"
              kaios run --default "task"
              kaios runs
              kaios ps <run-id>
              kaios inspect <run-id>
              kaios report <run-id>
              kaios export <run-id> [--out artifact.md]
              kaios doctor
            """.trimIndent(),
        )
    }

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

    private fun columnWidth(index: Int): Int = when (index) {
        0 -> 6
        1 -> 12
        2 -> 10
        3 -> 8
        4 -> 8
        5 -> 8
        else -> 10
    }

    private fun defaultConfigPath(): Path = workingDir.resolve(KAIOS_CONFIG_FILE).normalize()

    private fun toolRegistry() =
        builtInToolRegistry(fileRoot = workingDir.resolve(".kaios").resolve("files"))

    private fun contextLoader() =
        ContextLoader(
            workingDir = workingDir,
            maxChars = env("KAIOS_CONTEXT_MAX_CHARS")?.toIntOrNull()?.coerceAtLeast(1) ?: 80_000,
        )

    private fun resolvePath(value: String): Path {
        val path = Paths.get(value)
        return if (path.isAbsolute) path.normalize() else workingDir.resolve(path).normalize()
    }

    private fun defaultArtifactPath(runId: RunId): Path =
        artifactRoot.resolve("${runId.value}.md").normalize()

    private fun writeArtifact(snapshot: StoredRunSnapshot, path: Path, force: Boolean): Path {
        if (path.exists() && !force) {
            error("Artifact '$path' already exists. Use --force to overwrite it.")
        }
        path.parent?.let { Files.createDirectories(it) }
        path.writeText(artifactExporter.render(snapshot))
        return path
    }

    private fun displayPath(path: Path): String =
        if (path.startsWith(workingDir)) {
            workingDir.relativize(path).toString()
        } else {
            path.toString()
        }

    private fun parseRunCommand(args: List<String>): RunCommand {
        var configPath: Path? = null
        var useBuiltInDefault = false
        var outputPath: Path? = null
        var forceOutput = false
        val contextPaths = mutableListOf<Path>()
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
                arg == "--force-output" -> {
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
                arg == "--" -> {
                    taskParts += args.drop(index + 1)
                    index = args.size
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
        return RunCommand(task, configPath, useBuiltInDefault, outputPath, forceOutput, contextPaths)
    }

    private fun parseInitCommand(args: List<String>): InitCommand {
        var configPath = defaultConfigPath()
        var force = false
        var templateId = "default"
        var listTemplates = false
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

        return InitCommand(configPath, force, templateId, listTemplates)
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

    private fun parseExportCommand(args: List<String>): ExportCommand {
        val runId = args.firstOrNull()?.let(::RunId) ?: error("Run id is required.")
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

        return ExportCommand(runId, outputPath ?: defaultArtifactPath(runId), force)
    }

    private fun printTemplates(out: PrintStream) {
        out.println("TEMPLATES")
        projectConfigTemplates.forEach { template ->
            out.println("${template.id.padEnd(12)} ${template.description}")
        }
    }
}

private data class RunCommand(
    val task: String,
    val configPath: Path?,
    val useBuiltInDefault: Boolean,
    val outputPath: Path?,
    val forceOutput: Boolean,
    val contextPaths: List<Path>,
)

private data class InitCommand(
    val configPath: Path,
    val force: Boolean,
    val templateId: String,
    val listTemplates: Boolean,
)

private data class ExportCommand(
    val runId: RunId,
    val outputPath: Path,
    val force: Boolean,
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
