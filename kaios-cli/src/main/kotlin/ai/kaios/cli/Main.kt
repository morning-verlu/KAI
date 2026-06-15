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
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val KAIOS_VERSION = "0.1.84"
private const val CI_AGENT_GATE_ARTIFACT_NAME = "kaios-agent-gate"
private const val CI_WORKFLOW_PUSH_NOTE = "Pushing .github/workflows/kaios.yml may require GitHub workflow permission/scope."
private const val PROCESS_TRACE_SCHEMA = "kaios.process-trace/v1"
private const val RUN_CAPSULE_SCHEMA = "kaios.run-capsule/v1"
private const val RUN_REPLAY_SCHEMA = "kaios.run-replay/v1"
private const val RUN_DIFF_SCHEMA = "kaios.run-diff/v1"
private const val RUN_EVIDENCE_SCHEMA = "kaios.evidence/v1"
private const val DOCTOR_SCHEMA = "kaios.doctor/v1"
private const val DOCTOR_FIX_SCHEMA = "kaios.doctor-fix/v1"
private const val NEXT_SCHEMA = "kaios.next/v1"
private const val RUNS_SCHEMA = "kaios.runs/v1"
private const val CONFIG_VALIDATION_SCHEMA = "kaios.config-validation/v1"
private const val BUG_REPORT_SCHEMA = "kaios.bug-report/v1"
private const val SETUP_SCHEMA = "kaios.setup/v1"
private const val VERIFY_SCHEMA = "kaios.verify/v1"
private const val QUICKSTART_SCHEMA = "kaios.quickstart/v1"
private const val EVIDENCE_DIFF_CHANGE_LIMIT = 5

private val TOP_LEVEL_COMMANDS = listOf(
    "quickstart",
    "next",
    "setup",
    "gate",
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
    "replay",
    "diff",
    "evidence",
    "report",
    "export",
    "doctor",
    "bug-report",
    "version",
)

private val TOP_LEVEL_COMMAND_ALIASES = mapOf(
    "start" to "quickstart",
    "onboard" to "quickstart",
    "analyse" to "analyze",
    "ls" to "runs",
    "list" to "runs",
    "proc" to "ps",
    "process" to "ps",
    "status" to "doctor",
    "audit" to "evidence",
    "proof" to "evidence",
)

private val CONFIG_COMMANDS = listOf("templates", "validate", "show")
private val CI_AGENT_GATE_ARTIFACT_PATHS = listOf(
    "artifacts/kaios-verify.json",
    "artifacts/kaios-run.capsule.json",
    "artifacts/kaios-bug-report.json",
)

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

        val inputCommand = args.first()
        val command = resolveTopLevelCommand(inputCommand)
        val commandArgs = args.drop(1)
        return when (command) {
            "quickstart" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("quickstart")) else runQuickstart(commandArgs, out, err)
            "next" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("next")) else printWorkspaceNext(commandArgs, out, err)
            "setup" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("setup")) else setupProject(commandArgs, out, err)
            "gate" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("gate")) else runAgentGate(commandArgs, out, err)
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
            "replay" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("replay")) else replayCapsule(commandArgs, out, err)
            "diff" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("diff")) else diffCapsules(commandArgs, out, err)
            "evidence" -> if (isHelp(commandArgs)) printCommandHelp(out, commandHelp("evidence")) else evidenceRun(commandArgs, out, err)
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
                err.println("Unknown command '$inputCommand'.")
                printSuggestion(err, inputCommand, TOP_LEVEL_COMMANDS, TOP_LEVEL_COMMAND_ALIASES, "kaios")
                err.println("Run 'kaios help' for available commands.")
                printUsage(err)
                1
            }
        }
    }

    private fun isHelp(args: List<String>): Boolean =
        args.size == 1 && args.first() in setOf("help", "--help", "-h")

    private fun resolveTopLevelCommand(input: String): String =
        TOP_LEVEL_COMMAND_ALIASES[input.lowercase().trim()] ?: input

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

    private fun renderNextCommands(stream: PrintStream, commands: List<String>) {
        if (commands.isEmpty()) return
        stream.println()
        stream.println("next:")
        commands.forEach { command -> stream.println("  $command") }
    }

    private fun nextActions(commands: List<String>, vararg preferred: NextAction): List<NextAction> {
        val preferredByCommand = preferred.associateBy { it.command }
        return commands.map { command -> preferredByCommand[command] ?: nextAction(command) }
    }

    private fun nextAction(command: String): NextAction {
        val id = nextActionId(command)
        return NextAction(
            id = id,
            command = command,
            reason = nextActionReason(id),
        )
    }

    private fun nextActionId(command: String): String =
        when {
            command == "fix failed checks above" -> "fix-failed-checks"
            command.startsWith("fix ") -> "repair-config"
            command.startsWith("git add ") -> "stage-generated-files"
            command.startsWith("kaios quickstart") -> "quickstart"
            command.startsWith("kaios doctor") && command.contains(" --fix") -> "repair-project"
            command.startsWith("kaios setup ") && command.contains(" --force") -> "regenerate-config"
            command.startsWith("kaios setup ") || command == "kaios setup" -> "setup-project"
            command.startsWith("kaios config validate ") -> "validate-config"
            command.startsWith("kaios config show ") -> "show-config"
            command == "kaios gate" || command.startsWith("kaios gate ") -> "verify-project"
            command.startsWith("kaios verify ") -> "verify-project"
            command.startsWith("kaios demo") -> "run-demo"
            command.startsWith("kaios run ") -> "run-workflow"
            command.startsWith("kaios analyze ") -> "analyze-workspace"
            command == "kaios ps" || command.startsWith("kaios ps ") -> "show-processes"
            command == "kaios inspect" || command.startsWith("kaios inspect ") -> "inspect-run"
            command.startsWith("kaios trace") && command.contains(" --check") -> "check-trace"
            command == "kaios trace" || command.startsWith("kaios trace ") -> "view-trace"
            command.startsWith("kaios evidence") && command.contains(" --baseline ") -> "compare-evidence"
            command == "kaios evidence" || command.startsWith("kaios evidence ") -> "package-evidence"
            command == "kaios capsule" || command.startsWith("kaios capsule ") -> "validate-capsule"
            command.startsWith("kaios replay ") -> "replay-capsule"
            command.startsWith("kaios diff ") -> "diff-capsules"
            command == "kaios report" || command.startsWith("kaios report ") -> "open-report"
            command == "kaios export" || command.startsWith("kaios export ") -> "export-artifact"
            command.startsWith("kaios bug-report") -> "collect-support-report"
            command.startsWith("kaios doctor") -> "run-diagnostics"
            else -> "next"
        }

    private fun nextActionReason(id: String): String =
        when (id) {
            "fix-failed-checks" -> "Resolve failed diagnostics before retrying the workflow."
            "repair-config" -> "Repair the workflow config or regenerate it safely."
            "repair-project" -> "Preview or apply the safest local repair for failed diagnostics."
            "stage-generated-files" -> "Stage generated config and CI gate files for review."
            "quickstart" -> "Run the no-key onboarding gate and create inspectable evidence."
            "setup-project" -> "Create a validated local workflow and optional CI gate."
            "regenerate-config" -> "Regenerate the invalid workflow config and optional CI gate."
            "validate-config" -> "Check the workflow contract without starting agents."
            "show-config" -> "Inspect agents, tools, dependencies, and fallback routes."
            "verify-project" -> "Run the deterministic readiness gate and write evidence when requested."
            "run-demo" -> "Create a no-key run snapshot for inspection and support."
            "run-workflow" -> "Run an inspectable agent workflow for a real task."
            "analyze-workspace" -> "Generate a deterministic workspace report without a model key."
            "show-processes" -> "Inspect agent process metrics for the run."
            "inspect-run" -> "Read final output and lifecycle events for the run."
            "check-trace" -> "Validate the saved process trace contract."
            "view-trace" -> "Inspect the saved process trace."
            "compare-evidence" -> "Compare current evidence against a baseline capsule."
            "package-evidence" -> "Package, validate, replay, and optionally diff run evidence."
            "validate-capsule" -> "Validate a portable run capsule."
            "replay-capsule" -> "Replay the capsule offline to confirm determinism."
            "diff-capsules" -> "Compare two capsules using stable runtime signatures."
            "open-report" -> "Open the saved Agent Process Manager report."
            "export-artifact" -> "Export the saved run as a Markdown artifact."
            "collect-support-report" -> "Generate a safe support bundle for issues or handoff."
            "run-diagnostics" -> "Run local diagnostics; add --json when automation needs structured output."
            else -> "Continue with the next recommended command."
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
            args.size == 1 -> resolveTopLevelCommand(args.first())
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
            "quickstart" -> CommandHelp(
                usage = "kaios quickstart [--dry-run] [--no-ci|--local] [--force] [--json|--format json]",
                summary = "Run the no-key onboarding path: demo, setup, optional CI gate, verify evidence, and next moves.",
                examples = listOf(
                    "kaios quickstart --dry-run",
                    "kaios quickstart",
                    "kaios quickstart --no-ci",
                    "kaios quickstart --json",
                    "kaios quickstart --force",
                ),
                notes = listOf(
                    "The command uses the deterministic mock provider; no API key is required.",
                    "Use --dry-run to preview generated files and commands without writing anything.",
                    "Use --no-ci or --local when you want a local-only workflow without writing .github/workflows/kaios.yml.",
                    "Existing config and CI files are kept unless --force is passed.",
                    "Evidence is written to a quickstart-owned capsule so repeated runs stay low-friction.",
                    "JSON output uses schema $QUICKSTART_SCHEMA for onboarding checks and docs automation.",
                ),
            )
            "next" -> CommandHelp(
                usage = "kaios next [--config kaios.json] [--json|--format json]",
                summary = "Print the single best next command for the current workspace state.",
                examples = listOf(
                    "kaios next",
                    "kaios next --json",
                    "kaios next --config workflows/research.json",
                ),
                notes = listOf(
                    "The command is read-only: it inspects doctor diagnostics, config validation, latest run state, and trace validity.",
                    "It chooses repair before verify, verify before inspection, and inspection once evidence is healthy.",
                    "JSON output uses schema $NEXT_SCHEMA for onboarding, support bots, and docs automation.",
                ),
            )
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
            "gate" -> CommandHelp(
                usage = "kaios gate [--config kaios.json] [--baseline capsule.json] [--check] [--summary-out summary.md] [--json|--format json]",
                summary = "Run the production Agent Gate with readiness checks and portable evidence enabled by default.",
                examples = listOf(
                    "kaios gate",
                    "kaios gate --json",
                    "kaios gate --summary-out \$GITHUB_STEP_SUMMARY",
                    "kaios gate --baseline artifacts/baseline.capsule.json --check",
                    "kaios gate --config workflows/research.json",
                ),
                notes = listOf(
                    "Equivalent to 'kaios verify --evidence --force' with the same $VERIFY_SCHEMA JSON contract.",
                    "The gate validates doctor diagnostics, project config, a deterministic mock smoke workflow, the process trace contract, offline replay, and optional baseline diff.",
                    "It writes artifacts/kaios-run.capsule.json by default and overwrites that gate artifact safely on repeat runs.",
                    "Use --summary-out to append a Markdown Agent Gate summary to a local file or \$GITHUB_STEP_SUMMARY in CI.",
                    "Use 'kaios verify' when you need lower-level control over evidence output protection.",
                ),
            )
            "verify" -> CommandHelp(
                usage = "kaios verify [--config kaios.json] [--evidence|--evidence-out capsule.json] [--baseline capsule.json] [--check] [--force] [--summary-out summary.md] [--json|--format json]",
                summary = "Run the one-command readiness and evidence gate for local projects and CI.",
                examples = listOf(
                    "kaios verify",
                    "kaios verify --config kaios.json",
                    "kaios verify --config kaios.json --evidence --force",
                    "kaios verify --config kaios.json --evidence --summary-out artifacts/kaios-summary.md --force",
                    "kaios verify --config kaios.json --evidence --baseline artifacts/baseline.capsule.json --check --force",
                    "kaios verify --config kaios.json --evidence-out artifacts/custom.capsule.json --force",
                    "kaios verify --json",
                ),
                notes = listOf(
                    "The gate checks doctor diagnostics, validates project config, runs a deterministic mock smoke workflow, and validates the process trace contract.",
                    "Use --evidence to write artifacts/kaios-run.capsule.json, or --evidence-out for a custom capsule path.",
                    "Use --summary-out to append a Markdown summary for GitHub Actions step summaries or release notes.",
                    "--check exits 1 when the baseline evidence differs, and 2 when readiness or evidence validation fails.",
                    "It writes a normal run snapshot under .kaios/runs/ so ps, inspect, trace, capsule, evidence, and bug-report keep working.",
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
                    "After a run, use 'kaios ps', 'kaios inspect', 'kaios trace', and 'kaios evidence'.",
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
                    "Omit the run id on ps, inspect, trace, capsule, evidence, report, or export to use the latest saved run.",
                    "JSON output uses schema $RUNS_SCHEMA for Agent Desktop, CI, and local tooling.",
                ),
            )
            "ps" -> CommandHelp(
                usage = "kaios ps [run-id|latest]",
                summary = "Print the agent process table for a saved run.",
                examples = listOf(
                    "kaios ps",
                    "kaios ps run-97381ae9",
                ),
                notes = listOf("Tokens behave like CPU, context size like memory, and tool calls like syscalls."),
            )
            "inspect" -> CommandHelp(
                usage = "kaios inspect [run-id|latest]",
                summary = "Print final output and lifecycle events for a saved run.",
                examples = listOf(
                    "kaios inspect",
                    "kaios inspect run-97381ae9",
                ),
            )
            "trace" -> CommandHelp(
                usage = "kaios trace [run-id|latest] [--format text|json] [--out trace.json] [--force] [--check]",
                summary = "Print a KAI Process Trace with process metrics, execution path, event counts, and timeline.",
                examples = listOf(
                    "kaios trace",
                    "kaios trace --json",
                    "kaios trace --check",
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
                usage = "kaios capsule [run-id|latest]|--file capsule.json [--json] [--out capsule.json] [--force] [--check]",
                summary = "Build a portable run capsule with snapshot, trace, provenance hashes, and replay commands.",
                examples = listOf(
                    "kaios capsule",
                    "kaios capsule --check",
                    "kaios capsule --json",
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
            "replay" -> CommandHelp(
                usage = "kaios replay <capsule.json>|--file capsule.json [--json|--format json]",
                summary = "Replay a run capsule offline by rebuilding its trace from the embedded snapshot.",
                examples = listOf(
                    "kaios replay artifacts/run.capsule.json",
                    "kaios replay --file artifacts/run.capsule.json",
                    "kaios replay --file artifacts/run.capsule.json --json",
                ),
                notes = listOf(
                    "Replay never calls a model provider and does not require the original .kaios/runs snapshot.",
                    "JSON output uses schema $RUN_REPLAY_SCHEMA for CI, issue triage, and future Agent Desktop imports.",
                    "The replay is valid only when the capsule contract passes and the rebuilt trace matches the embedded trace.",
                ),
            )
            "diff" -> CommandHelp(
                usage = "kaios diff <left.capsule.json> <right.capsule.json> [--json|--format json] [--check]",
                summary = "Compare two run capsules offline using their stable runtime signatures.",
                examples = listOf(
                    "kaios diff artifacts/baseline.capsule.json artifacts/current.capsule.json",
                    "kaios diff --left artifacts/baseline.capsule.json --right artifacts/current.capsule.json --json",
                    "kaios diff artifacts/baseline.capsule.json artifacts/current.capsule.json --check",
                ),
                notes = listOf(
                    "Diff never calls a model provider and ignores run ids, timestamps, and duration noise.",
                    "JSON output uses schema $RUN_DIFF_SCHEMA for CI regression checks and release gates.",
                    "--check exits 1 when valid capsules differ, and 2 when either capsule is invalid.",
                ),
            )
            "evidence" -> CommandHelp(
                usage = "kaios evidence [run-id|latest] [--out capsule.json] [--baseline capsule.json] [--json|--format json] [--check] [--force]",
                summary = "Create a CI-ready evidence bundle by packaging, validating, replaying, and optionally diffing a run capsule.",
                examples = listOf(
                    "kaios evidence",
                    "kaios evidence --out artifacts/run.capsule.json --force",
                    "kaios evidence --out artifacts/run.capsule.json --baseline artifacts/baseline.capsule.json --check --force",
                    "kaios evidence run-97381ae9 --json --out artifacts/run.capsule.json --force",
                ),
                notes = listOf(
                    "Evidence never calls a model provider; it works from an existing .kaios/runs snapshot.",
                    "JSON output uses schema $RUN_EVIDENCE_SCHEMA for CI gates, release audits, and future Agent Desktop imports.",
                    "--check exits 1 when the baseline diff is different, and 2 when capsule or replay validation fails.",
                    "Existing capsule files are protected unless --force is passed.",
                ),
            )
            "report" -> CommandHelp(
                usage = "kaios report [run-id|latest]",
                summary = "Generate a standalone HTML Agent Process Manager report.",
                examples = listOf(
                    "kaios report",
                    "kaios report run-97381ae9",
                ),
                notes = listOf("Reports are written under .kaios/reports/ by default."),
            )
            "export" -> CommandHelp(
                usage = "kaios export [run-id|latest] [--out artifact.md] [--force]",
                summary = "Export a saved run snapshot as a Markdown artifact.",
                examples = listOf(
                    "kaios export",
                    "kaios export run-97381ae9",
                    "kaios export run-97381ae9 --out artifacts/run.md --force",
                ),
            )
            "doctor" -> CommandHelp(
                usage = "kaios doctor [--config kaios.json] [--fix] [--dry-run] [--ci] [--force] [--json|--format json]",
                summary = "Check the local runtime and optionally repair missing or invalid project workflow files.",
                examples = listOf(
                    "kaios doctor",
                    "kaios doctor --fix --dry-run",
                    "kaios doctor --fix",
                    "kaios doctor --fix --ci",
                    "kaios doctor --config workflows/research.json",
                    "kaios doctor --json",
                ),
                notes = listOf(
                    "Run this first when a command behaves differently across machines.",
                    "Use --fix to create or repair the configured kaios.json using the research template.",
                    "Use --dry-run with --fix to preview workflow and CI writes before touching project files.",
                    "Existing config and CI files are kept unless --force is passed.",
                    "JSON output uses schema $DOCTOR_SCHEMA for CI and issue diagnostics.",
                    "Fix JSON output uses schema $DOCTOR_FIX_SCHEMA for repair automation.",
                ),
            )
            "bug-report" -> CommandHelp(
                usage = "kaios bug-report [--config kaios.json] [--json|--format markdown|json] [--out report.md] [--force]",
                summary = "Generate a safe support report for GitHub issues and team handoff.",
                examples = listOf(
                    "kaios bug-report",
                    "kaios bug-report --config workflows/research.json",
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

    private fun runQuickstart(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseQuickstartCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "quickstart", error.message)
        }
        val report = buildQuickstartReport(command)

        when (command.format) {
            QuickstartFormat.Text -> renderQuickstartText(report, out)
            QuickstartFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (report.status == "ready" || report.status == "planned") 0 else 2
    }

    private fun buildQuickstartReport(command: QuickstartCommand): QuickstartReport {
        val plan = quickstartPlan(command)
        if (command.dryRun) {
            val next = listOf(quickstartRunCommand(command), "kaios help quickstart").distinct()
            return QuickstartReport(
                schema = QUICKSTART_SCHEMA,
                version = KAIOS_VERSION,
                cwd = workingDir.toString(),
                status = "planned",
                plan = plan,
                demo = null,
                setup = null,
                verify = null,
                errors = emptyList(),
                next = next,
                nextActions = nextActions(next),
            )
        }
        val errors = mutableListOf<String>()
        val demo = runCatching { createDemoRun() }
            .getOrElse { error ->
                errors += error.message ?: "demo run failed."
                null
            }
        val setup = runCatching {
            buildSetupReport(
                SetupCommand(
                    configPath = defaultConfigPath(),
                    force = command.force,
                    templateId = "research",
                    writeCi = command.writeCi,
                    format = SetupFormat.Text,
                ),
            )
        }.getOrElse { error ->
            errors += error.message ?: "setup failed."
            null
        }
        val verify = setup?.let {
            runCatching {
                buildVerifyReport(
                    VerifyCommand(
                        configPath = defaultConfigPath(),
                        format = VerifyFormat.Text,
                        evidenceOutputPath = defaultQuickstartEvidencePath(),
                        evidenceForce = true,
                    ),
                )
            }.getOrElse { error ->
                errors += error.message ?: "verify failed."
                null
            }
        }
        val setupReady = setup?.let { it.doctor.summary.failed == 0 && it.validation.valid } == true
        val verifyReady = verify?.let { it.status == "ready" && it.evidence?.valid != false } == true
        val status = if (errors.isEmpty() && demo?.success == true && setupReady && verifyReady) {
            "ready"
        } else {
            "failed"
        }
        val next = quickstartNextCommands(status, setup, verify)
        return QuickstartReport(
            schema = QUICKSTART_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            status = status,
            plan = plan,
            demo = demo,
            setup = setup,
            verify = verify,
            errors = (errors + (verify?.errors ?: emptyList())).distinct(),
            next = next,
            nextActions = nextActions(next),
        )
    }

    private fun quickstartPlan(command: QuickstartCommand): QuickstartPlan {
        val configPath = defaultConfigPath()
        val ciPath = defaultCiWorkflowPath()
        val evidencePath = defaultQuickstartEvidencePath()
        val writes = buildList {
            add(
                QuickstartPlannedWrite(
                    id = "config",
                    path = displayPath(configPath),
                    action = plannedFileAction(configPath, command.force),
                    reason = "research workflow config",
                ),
            )
            add(
                QuickstartPlannedWrite(
                    id = "ci",
                    path = if (command.writeCi) displayPath(ciPath) else null,
                    action = if (command.writeCi) plannedFileAction(ciPath, command.force) else SetupFileAction.Skipped.id,
                    reason = "GitHub Actions Agent Gate",
                ),
            )
            add(
                QuickstartPlannedWrite(
                    id = "run_snapshots",
                    path = displayPath(snapshotRoot.resolve("<run-id>.json")),
                    action = SetupFileAction.Created.id,
                    reason = "demo and verify run snapshots",
                ),
            )
            add(
                QuickstartPlannedWrite(
                    id = "run_artifacts",
                    path = "${displayPath(artifactRoot.resolve("<run-id>.md"))} and ${displayPath(artifactRoot.resolve("<run-id>.trace.json"))}",
                    action = SetupFileAction.Created.id,
                    reason = "demo artifact and process trace",
                ),
            )
            add(
                QuickstartPlannedWrite(
                    id = "evidence_capsule",
                    path = displayPath(evidencePath),
                    action = plannedFileAction(evidencePath, force = true),
                    reason = "quickstart-owned evidence capsule",
                ),
            )
        }
        return QuickstartPlan(
            dryRun = command.dryRun,
            mode = if (command.writeCi) "with-ci" else "local-only",
            writes = writes,
            commands = listOf(
                "kaios demo",
                quickstartSetupCommand(command),
                "kaios verify --config kaios.json --evidence-out ${displayPath(evidencePath)} --force",
            ),
            notes = buildList {
                add("No API key is required; quickstart uses the deterministic mock provider.")
                if (command.dryRun) add("Dry run only previews the plan; no files were written.")
                if (!command.force) add("Existing config and CI files are kept. Pass --force to overwrite them.")
                if (command.writeCi) add(CI_WORKFLOW_PUSH_NOTE)
            },
        )
    }

    private fun plannedFileAction(path: Path, force: Boolean): String =
        when {
            path.exists() && force -> SetupFileAction.Overwritten.id
            path.exists() -> SetupFileAction.Existing.id
            else -> SetupFileAction.Created.id
        }

    private fun quickstartSetupCommand(command: QuickstartCommand): String =
        buildString {
            append("kaios setup --template research")
            if (command.writeCi) append(" --ci")
            if (command.force) append(" --force")
        }

    private fun quickstartRunCommand(command: QuickstartCommand): String =
        buildString {
            append("kaios quickstart")
            if (!command.writeCi) append(" --no-ci")
            if (command.force) append(" --force")
        }

    private fun quickstartNextCommands(
        status: String,
        setup: SetupReport?,
        verify: VerifyReport?,
    ): List<String> =
        buildList {
            if (status == "ready") {
                add("kaios ps")
                add("kaios inspect")
                add("kaios trace --check")
                add(firstProjectRunCommand())
                quickstartStageCommand(setup)?.let(::add)
            } else {
                setup?.next?.let(::addAll)
                verify?.next?.let(::addAll)
                if (setup == null) add("kaios setup --ci")
                if (verify == null) add("kaios gate")
                add("kaios doctor --json")
            }
        }.distinct()

    private fun quickstartStageCommand(setup: SetupReport?): String? {
        if (setup == null) return null
        val paths = buildList {
            setup.config.path?.let { add(displayPath(Paths.get(it))) }
            setup.ci.path?.let { add(displayPath(Paths.get(it))) }
        }.distinct()
        if (paths.isEmpty()) return null
        return "git add ${paths.joinToString(" ")}"
    }

    private fun renderQuickstartText(report: QuickstartReport, out: PrintStream) {
        out.println("KAI OS quickstart")
        out.println("schema: ${report.schema}")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println("status: ${report.status}")
        if (report.status == "planned") {
            out.println()
            renderQuickstartPlan(report.plan, out)
            if (report.next.isNotEmpty()) {
                out.println()
                out.println("next:")
                report.next.forEach { command -> out.println("  $command") }
            }
            return
        }
        out.println()
        out.println("steps:")
        val demo = report.demo
        if (demo == null) {
            out.println("  demo: failed")
        } else {
            out.println("  demo: ${if (demo.success) "ready" else "failed"} run=${demo.runId} processes=${demo.processCount}")
        }
        val setup = report.setup
        if (setup == null) {
            out.println("  setup: failed")
        } else {
            out.println("  setup: ${if (setup.validation.valid) "ready" else "failed"} config=${setup.config.action} ci=${setup.ci.action}")
        }
        val verify = report.verify
        if (verify == null) {
            out.println("  verify: failed")
        } else {
            out.println("  verify: ${verify.status} run=${verify.run?.runId ?: "-"} evidence=${verify.evidence?.status ?: "skipped"}")
        }
        out.println()
        out.println("artifacts:")
        demo?.let {
            out.println("  demo_snapshot: ${it.snapshot}")
            out.println("  demo_artifact: ${it.artifact}")
            out.println("  demo_trace: ${it.trace}")
        }
        setup?.let {
            out.println("  config: ${it.config.path ?: "-"}")
            out.println("  ci: ${it.ci.path ?: "-"}")
            it.ciArtifact?.let { artifact ->
                out.println("  ci_artifact: ${artifact.name}")
                out.println("  ci_artifact_paths: ${artifact.paths.joinToString(", ")}")
                out.println("  ci_push_note: ${artifact.pushPermissionNote}")
            }
        }
        verify?.run?.let { run -> out.println("  verify_snapshot: ${run.snapshot}") }
        verify?.evidence?.let { evidence -> out.println("  evidence_capsule: ${evidence.capsulePath}") }
        if (report.errors.isNotEmpty()) {
            out.println()
            out.println("errors:")
            report.errors.forEach { error -> out.println("  - ${singleLine(error)}") }
        }
        out.println()
        out.println("next:")
        report.next.forEach { command -> out.println("  $command") }
    }

    private fun renderQuickstartPlan(plan: QuickstartPlan, out: PrintStream) {
        out.println("plan:")
        out.println("  mode: ${plan.mode}")
        out.println("  dry_run: ${plan.dryRun}")
        out.println("  writes:")
        plan.writes.forEach { write ->
            val path = write.path?.let { " ($it)" }.orEmpty()
            out.println("    ${write.id}: ${write.action}$path")
        }
        out.println("  commands:")
        plan.commands.forEach { command -> out.println("    $command") }
        if (plan.notes.isNotEmpty()) {
            out.println("  notes:")
            plan.notes.forEach { note -> out.println("    - $note") }
        }
    }

    private fun runDemo(args: List<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isNotEmpty()) {
            return printCommandUsageError(err, "demo", "Demo does not accept arguments.")
        }

        val demo = runCatching { createDemoRun() }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        renderDemoText(demo, out)
        return if (demo.success) 0 else 2
    }

    private fun createDemoRun(): DemoRunReport {
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
        val artifactPath = writeArtifact(snapshot, defaultArtifactPath(result.runId), false)
        val tracePath = writeTraceJson(snapshot, defaultTracePath(result.runId), false)
        return DemoRunReport(
            runId = result.runId.value,
            success = result.success,
            snapshot = snapshotPath.toString(),
            artifact = artifactPath.toString(),
            trace = tracePath.toString(),
            processCount = snapshot.processes.size,
            tokenTotal = snapshot.processes.sumOf { it.tokens },
            syscallCount = snapshot.processes.sumOf { it.syscallCount },
            output = result.finalOutput,
            processes = snapshot.processes,
        )
    }

    private fun renderDemoText(demo: DemoRunReport, out: PrintStream) {
        out.println("KAI OS demo")
        out.println("provider: mock (deterministic, no API key)")
        out.println("run_id: ${demo.runId}")
        out.println("success: ${demo.success}")
        out.println("snapshot: ${demo.snapshot}")
        out.println("artifact: ${demo.artifact}")
        out.println("trace: ${demo.trace}")
        out.println()
        out.println("processes:")
        out.println(formatProcessHeader())
        demo.processes.forEach { process -> out.println(formatProcess(process)) }
        out.println()
        out.println("output:")
        out.println(demo.output)
        out.println()
        out.println("next:")
        out.println("  kaios ps")
        out.println("  kaios inspect")
        out.println("  kaios trace --json")
        out.println("  kaios evidence")
        out.println("  kaios run --index . --out artifacts/project.md --trace-out artifacts/trace.json --force \"summarize this project\"")
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
        out.println("  kaios ps")
        out.println("  kaios inspect")
        out.println("  kaios trace")
        out.println("  kaios evidence")
        out.println("  kaios report")
        out.println("  kaios export")
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
        val report = runCatching { buildSetupReport(command) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }

        when (command.format) {
            SetupFormat.Text -> renderSetupText(report, out)
            SetupFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (report.doctor.summary.failed > 0 || !report.validation.valid) 1 else 0
    }

    private fun buildSetupReport(command: SetupCommand): SetupReport {
        requireProjectTemplate(command.templateId)
        val configPath = command.configPath
        val configFile = setupConfigFile(configPath, command.templateId, command.force)
        val validation = buildConfigValidationReport(configPath)
        val ciFile = if (command.writeCi && validation.valid) {
            setupCiFile(defaultCiWorkflowPath(), configPath, command.force)
        } else {
            SetupFileReport(path = null, action = SetupFileAction.Skipped.id)
        }
        val doctor = buildDoctorReport(configPath, runtimeConfigFailureStatus = DoctorStatus.WARN)
        val next = setupNextCommands(validation, ciFile, command)
        return SetupReport(
            schema = SETUP_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            requestedTemplate = command.templateId,
            config = configFile,
            ci = ciFile,
            ciArtifact = ciFile.path?.let { ciArtifactReport() },
            doctor = doctor,
            validation = validation,
            next = next,
            nextActions = nextActions(next),
        )
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

    private fun ciArtifactReport(): SetupCiArtifact =
        SetupCiArtifact(
            name = CI_AGENT_GATE_ARTIFACT_NAME,
            paths = CI_AGENT_GATE_ARTIFACT_PATHS,
        )

    private fun setupNextCommands(
        validation: ConfigValidationReport,
        ciFile: SetupFileReport,
        command: SetupCommand,
    ): List<String> =
        buildList {
            add("kaios config validate --config ${displayPath(Paths.get(validation.config))} --json")
            if (validation.valid) {
                add(verifyEvidenceCommand(Paths.get(validation.config)))
                add("kaios ps")
            } else {
                add(setupForceCommand(command))
            }
            if (ciFile.path != null) {
                add("git add ${displayPath(Paths.get(validation.config))} ${displayPath(Paths.get(ciFile.path))}")
            }
            add(bugReportCommand(Paths.get(validation.config)))
        }.distinct()

    private fun verifyEvidenceCommand(configPath: Path): String =
        "kaios gate --config ${displayPath(configPath)}"

    private fun bugReportCommand(configPath: Path): String =
        if (configPath.toAbsolutePath().normalize() == defaultConfigPath().toAbsolutePath().normalize()) {
            "kaios bug-report"
        } else {
            "kaios bug-report --config ${displayPath(configPath)}"
        }

    private fun doctorJsonCommand(configPath: Path): String =
        if (configPath.toAbsolutePath().normalize() == defaultConfigPath().toAbsolutePath().normalize()) {
            "kaios doctor --json"
        } else {
            "kaios doctor --config ${displayPath(configPath)} --json"
        }

    private fun doctorTextCommand(configPath: Path): String =
        if (configPath.toAbsolutePath().normalize() == defaultConfigPath().toAbsolutePath().normalize()) {
            "kaios doctor"
        } else {
            "kaios doctor --config ${displayPath(configPath)}"
        }

    private fun setupCommand(configPath: Path): String =
        if (configPath.toAbsolutePath().normalize() == defaultConfigPath().toAbsolutePath().normalize()) {
            "kaios setup --ci"
        } else {
            "kaios setup --config ${displayPath(configPath)} --ci"
        }

    private fun setupForceCommand(command: SetupCommand): String =
        buildString {
            append("kaios setup")
            append(" --template ${command.templateId}")
            if (command.configPath.toAbsolutePath().normalize() != defaultConfigPath().toAbsolutePath().normalize()) {
                append(" --config ${displayPath(command.configPath)}")
            }
            if (command.writeCi) append(" --ci")
            append(" --force")
        }

    private fun setupForceCommand(configPath: Path): String =
        buildString {
            append("kaios setup")
            if (configPath.toAbsolutePath().normalize() != defaultConfigPath().toAbsolutePath().normalize()) {
                append(" --config ${displayPath(configPath)}")
            }
            append(" --ci --force")
        }

    private fun configRecoveryCommands(configPath: Path, includeValidate: Boolean = true): List<String> =
        buildList {
            if (configPath.exists()) {
                if (includeValidate) add("kaios config validate --config ${displayPath(configPath)} --json")
                add(doctorFixCommand(configPath, writeCi = true, force = true, dryRun = true))
                add(doctorFixCommand(configPath, writeCi = true, force = true))
                add(setupForceCommand(configPath))
            } else {
                add(doctorFixCommand(configPath, writeCi = true, dryRun = true))
                add(doctorFixCommand(configPath, writeCi = true))
                add(setupCommand(configPath))
            }
        }

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
        report.ciArtifact?.let { artifact ->
            out.println("ci_artifact: ${artifact.name}")
            out.println("ci_artifact_paths: ${artifact.paths.joinToString(", ")}")
            out.println("ci_push_note: ${artifact.pushPermissionNote}")
        }
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

    private fun runAgentGate(args: List<String>, out: PrintStream, err: PrintStream): Int =
        verifyProject(withGateDefaults(args), out, err, "KAI OS gate", "gate")

    private fun withGateDefaults(args: List<String>): List<String> {
        val requestsEvidence = args.any { arg ->
            arg == "--evidence" ||
                arg == "--evidence-out" ||
                arg == "--evidence-output" ||
                arg.startsWith("--evidence-out=") ||
                arg.startsWith("--evidence-output=") ||
                arg == "--baseline" ||
                arg == "--evidence-baseline" ||
                arg.startsWith("--baseline=") ||
                arg.startsWith("--evidence-baseline=")
        }
        val forcesEvidence = args.any { it == "--force" || it == "-f" || it == "--evidence-force" }
        return buildList {
            addAll(args)
            if (!requestsEvidence) add("--evidence")
            if (!forcesEvidence) add("--force")
        }
    }

    private fun verifyProject(
        args: List<String>,
        out: PrintStream,
        err: PrintStream,
        title: String = "KAI OS verify",
        commandName: String = "verify",
    ): Int {
        val command = runCatching { parseVerifyCommand(args, commandName) }.getOrElse { error ->
            return printCommandUsageError(err, commandName, error.message)
        }
        val report = buildVerifyReport(command)
        val summaryPath = command.summaryOutputPath?.let { outputPath ->
            runCatching { appendVerifySummary(report, title, command, outputPath) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
        }

        when (command.format) {
            VerifyFormat.Text -> renderVerifyText(report, out, title, summaryPath)
            VerifyFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return verifyExitCode(report, command)
    }

    private fun verifyExitCode(report: VerifyReport, command: VerifyCommand): Int =
        when {
            report.status != "ready" -> 2
            report.evidence?.valid == false -> 2
            command.evidenceCheck && report.evidence?.diff?.same == false -> 1
            else -> 0
        }

    private fun buildVerifyReport(command: VerifyCommand): VerifyReport {
        val configPath = command.configPath
        val doctor = buildDoctorReport(configPath, runtimeConfigFailureStatus = DoctorStatus.WARN)
        val config = buildConfigValidationReport(configPath)
        val errors = mutableListOf<String>()
        var run: VerifyRun? = null
        var trace: VerifyTrace? = null
        var evidence: RunEvidenceReport? = null

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
                if (command.evidenceRequested && result.success && traceIssues.isEmpty()) {
                    evidence = buildAndWriteRunEvidence(
                        snapshot = snapshot,
                        outputPath = command.evidenceOutputPath ?: defaultCapsulePath(result.runId),
                        baselinePath = command.evidenceBaselinePath,
                        forceOutput = command.evidenceForce,
                    )
                    if (evidence.valid == false) {
                        errors += "evidence contract failed."
                    }
                }
            }.onFailure { error ->
                errors += error.message ?: "verify smoke workflow failed."
            }
        }

        val status = if (errors.isEmpty() && run?.success == true && trace?.valid == true) "ready" else "failed"
        val next = verifyNextCommands(config, run, trace, evidence)
        val report = VerifyReport(
            schema = VERIFY_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            status = status,
            doctor = doctor,
            config = config,
            run = run,
            trace = trace,
            evidence = evidence,
            errors = errors.distinct(),
            next = next,
            nextActions = nextActions(next),
            diagnosis = VerifyDiagnosis(
                status = status,
                verdict = "",
                reasons = emptyList(),
                fixFirst = null,
                diffChanges = emptyList(),
            ),
        )
        return report.copy(diagnosis = verifyDiagnosis(report, command))
    }

    private fun verifyNextCommands(
        config: ConfigValidationReport,
        run: VerifyRun?,
        trace: VerifyTrace?,
        evidence: RunEvidenceReport?,
    ): List<String> =
        buildList {
            val configPath = Paths.get(config.config)
            if (!config.valid) {
                addAll(configRecoveryCommands(configPath))
            }
            if (run != null) {
                add("kaios ps")
                add("kaios inspect")
            }
            if (trace != null) {
                add(if (trace.valid) "kaios trace --check" else "kaios trace")
            }
            if (run != null) {
                if (evidence == null) add("kaios evidence")
            }
            add(bugReportCommand(configPath))
        }.distinct()

    private fun renderVerifyText(
        report: VerifyReport,
        out: PrintStream,
        title: String = "KAI OS verify",
        summaryPath: Path? = null,
    ) {
        out.println(title)
        out.println("schema: ${report.schema}")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println("status: ${report.status}")
        out.println("gate_status: ${report.diagnosis.status}")
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
        val evidence = report.evidence
        if (evidence == null) {
            out.println("evidence: skipped")
        } else {
            out.println("evidence: ${evidence.status} (${evidence.schema})")
            out.println("capsule: ${evidence.capsulePath}")
            out.println("replay: ${evidence.replay.status}")
            out.println("diff: ${evidence.diff.status}")
        }
        summaryPath?.let { out.println("summary: $it") }
        if (report.diagnosis.reasons.isNotEmpty()) {
            out.println()
            out.println("why:")
            report.diagnosis.reasons.forEach { reason -> out.println("  - ${singleLine(reason)}") }
        }
        report.diagnosis.fixFirst?.let { action ->
            out.println()
            out.println("fix_first: ${action.command}")
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
        ciPath?.let {
            out.println("created_ci: $it")
            val artifact = ciArtifactReport()
            out.println("ci_artifact: ${artifact.name}")
            out.println("ci_artifact_paths: ${artifact.paths.joinToString(", ")}")
        }
        out.println("template: ${command.templateId}")
        out.println()
        out.println("next:")
        out.println("  kaios config validate --config ${displayPath(path)} --json")
        out.println("  kaios config show --config ${displayPath(path)}")
        out.println("  ${verifyEvidenceCommand(path)}")
        out.println("  kaios run \"${template.exampleTask}\"")
        ciPath?.let { out.println("  git add ${displayPath(path)} ${displayPath(it)}") }
        out.println("  kaios ps")
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
                        renderNextCommands(out, configValidationNextCommands(path, valid = true))
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

    private fun configValidationReport(path: Path, workflow: Workflow?, errors: List<String>): ConfigValidationReport {
        val next = configValidationNextCommands(path, valid = errors.isEmpty())
        return ConfigValidationReport(
            schema = CONFIG_VALIDATION_SCHEMA,
            config = path.toString(),
            valid = errors.isEmpty(),
            workflowName = workflow?.name,
            agentCount = workflow?.nodes?.size ?: 0,
            agentIds = workflow?.nodes?.map { it.id }.orEmpty(),
            errors = errors,
            next = next,
            nextActions = nextActions(next),
        )
    }

    private fun configValidationNextCommands(path: Path, valid: Boolean): List<String> =
        if (valid) {
            listOf(
                verifyEvidenceCommand(path),
                "kaios config show --config ${displayPath(path)}",
            )
        } else {
            configRecoveryCommands(path, includeValidate = false)
        }

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
            err.println("Run '${setupCommand(path)}' to create a validated project workflow and CI gate.")
            err.println("Run 'kaios config templates' to choose a different template before setup.")
            err.println("Use '--config path/to/kaios.json' to inspect another config file.")
        } else {
            renderNextCommands(err, configValidationNextCommands(path, valid = false))
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
        out.println("Run 'kaios quickstart' to create a no-key onboarding run, project workflow, and evidence capsule.")
        out.println("Run 'kaios demo' to create a no-key sample run.")
        out.println("Run 'kaios setup --ci' to create a project workflow.")
        out.println("Run 'kaios gate' after setup to create an inspectable project run and evidence capsule.")
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
        val runIdText = args.firstOrNull() ?: "latest"
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
        val runIdText = args.firstOrNull() ?: "latest"
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
        if (command.fix) {
            val report = runCatching { buildDoctorFixReport(command) }.getOrElse { error ->
                err.println(error.message)
                return 1
            }
            when (command.format) {
                DoctorFormat.Text -> renderDoctorFixText(report, out)
                DoctorFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
            }
            return if (report.status == "fixed" || report.status == "planned") 0 else 2
        }

        val report = buildDoctorReport(command.configPath)

        when (command.format) {
            DoctorFormat.Text -> renderDoctorText(report, out)
            DoctorFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return if (report.summary.failed > 0) 2 else 0
    }

    private fun printWorkspaceNext(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseNextCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "next", error.message)
        }
        val report = buildNextReport(command.configPath)

        when (command.format) {
            NextFormat.Text -> renderNextText(report, out)
            NextFormat.Json -> out.println(TRACE_JSON.encodeToString(report))
        }

        return 0
    }

    private fun buildNextReport(configPath: Path): NextReport {
        val doctor = buildDoctorReport(configPath)
        val config = buildConfigValidationReport(configPath)
        val snapshots = runCatching { snapshotStore.list() }.getOrElse { emptyList() }
        val latestSnapshot = snapshots.firstOrNull()
        val latestRun = latestSnapshot?.let(::bugReportRun)
        val trace = latestSnapshot?.let(::bugReportTrace)
        val changes = detectWorkspaceGitChanges(workingDir)
        val reportNext = bugReportNextCommands(config, latestRun)
        val fixFirst = nextFixFirst(configPath, doctor, config, latestRun, trace, changes, reportNext)
        val action = fixFirst ?: nextHealthyAction(latestRun)
        val next = buildList {
            add(action.command)
            addAll(reportNext)
            add(bugReportCommand(configPath))
        }.distinct()

        return NextReport(
            schema = NEXT_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            status = nextStatus(doctor, config, latestRun, trace, changes),
            action = action,
            fixFirst = fixFirst,
            signals = nextSignals(doctor, config, latestRun, trace, changes),
            next = next,
            nextActions = nextActions(next, action),
        )
    }

    private fun nextFixFirst(
        configPath: Path,
        doctor: DoctorReport,
        config: ConfigValidationReport,
        latestRun: BugReportRun?,
        trace: BugReportTrace?,
        changes: WorkspaceChangeSummary,
        next: List<String>,
    ): NextAction? =
        when {
            !config.valid -> bugReportFixFirst(config, latestRun, trace, next)
            doctor.summary.failed > 0 -> nextAction(doctorTextCommand(configPath))
            changes.available && changes.dirty -> nextChangeAction(changes)
            latestRun == null -> bugReportFixFirst(config, latestRun, trace, next)
            trace?.valid == false -> bugReportFixFirst(config, latestRun, trace, next)
            else -> null
        }

    private fun nextChangeAction(changes: WorkspaceChangeSummary): NextAction =
        NextAction(
            id = "review-current-change",
            command = "kaios analyze .",
            reason = "Git working tree has ${changes.changedFiles} changed file(s); analyze current changes before running gates or packaging evidence.",
        )

    private fun nextHealthyAction(latestRun: BugReportRun?): NextAction =
        nextAction(if (latestRun?.success == false) "kaios inspect" else "kaios ps")

    private fun nextStatus(
        doctor: DoctorReport,
        config: ConfigValidationReport,
        latestRun: BugReportRun?,
        trace: BugReportTrace?,
        changes: WorkspaceChangeSummary,
    ): String =
        when {
            doctor.summary.failed > 0 || !config.valid -> "repair"
            changes.available && changes.dirty -> "review"
            latestRun == null -> "verify"
            trace?.valid == false -> "repair"
            !latestRun.success -> "inspect"
            else -> "ready"
        }

    private fun nextSignals(
        doctor: DoctorReport,
        config: ConfigValidationReport,
        latestRun: BugReportRun?,
        trace: BugReportTrace?,
        changes: WorkspaceChangeSummary,
    ): List<NextSignal> =
        buildList {
            add(
                NextSignal(
                    name = "doctor",
                    status = doctor.summary.status,
                    detail = "${doctor.summary.failed} failed, ${doctor.summary.warnings} warning(s)",
                ),
            )
            add(
                NextSignal(
                    name = "config",
                    status = if (config.valid) "valid" else "invalid",
                    detail = if (config.valid) {
                        "${config.workflowName ?: "workflow"} (${config.agentCount} agent process node(s))"
                    } else {
                        config.errors.firstOrNull()?.let(::singleLine) ?: "workflow config is invalid"
                    },
                ),
            )
            if (changes.available) {
                add(
                    NextSignal(
                        name = "changes",
                        status = if (changes.dirty) "dirty" else "clean",
                        detail = if (changes.dirty) {
                            "${changes.changedFiles} changed file(s), ${changes.untrackedFiles} untracked"
                        } else {
                            "Git working tree is clean"
                        },
                    ),
                )
            }
            add(
                NextSignal(
                    name = "latest_run",
                    status = when {
                        latestRun == null -> "missing"
                        latestRun.success -> "success"
                        else -> "failed"
                    },
                    detail = latestRun?.let { "${it.runId} ${singleLine(it.task)}" } ?: "no saved run snapshot",
                ),
            )
            add(
                NextSignal(
                    name = "trace",
                    status = when {
                        trace == null -> "missing"
                        trace.valid -> "valid"
                        else -> "invalid"
                    },
                    detail = when {
                        trace == null -> "no trace because no saved run snapshot exists"
                        trace.valid -> "${trace.processCount} process(es), ${trace.eventCount} event(s)"
                        else -> trace.issues.firstOrNull()?.let(::singleLine) ?: "process trace contract failed"
                    },
                ),
            )
        }

    private fun renderNextText(report: NextReport, out: PrintStream) {
        out.println("KAI OS next")
        out.println("schema: ${report.schema}")
        out.println("status: ${report.status}")
        out.println("command: ${report.action.command}")
        out.println("reason: ${report.action.reason}")
        report.fixFirst?.let { action -> out.println("fix_first: ${action.command}") }
        out.println()
        out.println("why:")
        report.signals.forEach { signal ->
            out.println("  ${signal.name}: ${signal.status} - ${signal.detail}")
        }
        renderNextCommands(out, report.next)
    }

    private fun bugReport(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseBugReportCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "bug-report", error.message)
        }
        val report = buildBugReport(command.configPath)
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

    private fun buildBugReport(configPath: Path): BugReport {
        val doctor = buildDoctorReport(configPath)
        val config = buildConfigValidationReport(configPath)
        val snapshots = runCatching { snapshotStore.list() }.getOrElse { emptyList() }
        val latestSnapshot = snapshots.firstOrNull()
        val latestRun = latestSnapshot?.let(::bugReportRun)
        val trace = latestSnapshot?.let(::bugReportTrace)

        val next = bugReportNextCommands(config, latestRun)
        val fixFirst = bugReportFixFirst(config, latestRun, trace, next)
        return BugReport(
            schema = BUG_REPORT_SCHEMA,
            version = KAIOS_VERSION,
            generatedAt = Instant.now().toString(),
            cwd = workingDir.toString(),
            files = BugReportFiles(
                config = configPath.toString(),
                runsDir = snapshotRoot.toAbsolutePath().normalize().toString(),
                reportsDir = reportRoot.toAbsolutePath().normalize().toString(),
                artifactsDir = artifactRoot.toAbsolutePath().normalize().toString(),
            ),
            doctor = doctor,
            config = config,
            latestRun = latestRun,
            trace = trace,
            fixFirst = fixFirst,
            next = next,
            nextActions = nextActions(next),
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
            val configPath = Paths.get(config.config)
            if (!config.valid) {
                addAll(configRecoveryCommands(configPath))
            } else {
                add(verifyEvidenceCommand(configPath))
            }
            if (latestRun == null) {
                add("kaios demo")
            } else {
                add("kaios ps")
                add("kaios trace --check")
                add("kaios evidence")
                add("kaios inspect")
            }
            add(doctorJsonCommand(configPath))
        }.distinct()

    private fun bugReportFixFirst(
        config: ConfigValidationReport,
        latestRun: BugReportRun?,
        trace: BugReportTrace?,
        next: List<String>,
    ): NextAction? {
        val command = when {
            !config.valid -> next.firstOrNull { command -> nextActionId(command) == "repair-project" }
                ?: next.firstOrNull { command ->
                    val id = nextActionId(command)
                    id == "regenerate-config" || id == "setup-project"
                }
            latestRun == null -> next.firstOrNull { command -> nextActionId(command) == "verify-project" }
                ?: next.firstOrNull { command -> nextActionId(command) == "run-demo" }
            trace?.valid == false -> next.firstOrNull { command -> nextActionId(command) == "check-trace" }
            else -> null
        }
        return command?.let(::nextAction)
    }

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
        report.fixFirst?.let { action ->
            appendLine("## Fix First")
            appendLine()
            appendLine("Run:")
            appendLine()
            appendLine("```bash")
            appendLine(action.command)
            appendLine("```")
            appendLine()
            appendLine(action.reason)
            appendLine()
        }
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
        val projectConfigStatus = checks.first { it.name == "project config" }.status
        val next = doctorNextCommands(failed, configPath, projectConfigStatus)

        val actions = nextActions(next)
        return DoctorReport(
            schema = DOCTOR_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            config = configPath.toString(),
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
            nextActions = actions,
        )
    }

    private fun doctorNextCommands(failed: Int, configPath: Path, projectConfigStatus: DoctorStatus): List<String> =
        buildList {
            if (failed > 0) add("fix failed checks above")
            add("kaios quickstart")
            add("kaios demo")
            when {
                configPath.exists() && projectConfigStatus == DoctorStatus.OK -> add(verifyEvidenceCommand(configPath))
                else -> {
                    val needsForce = configPath.exists()
                    add(doctorFixCommand(configPath, writeCi = true, force = needsForce, dryRun = true))
                    add(doctorFixCommand(configPath, writeCi = true, force = needsForce))
                    addAll(configRecoveryCommands(configPath))
                }
            }
            add("kaios analyze . --out artifacts/analysis.md --force")
        }.distinct()

    private fun buildDoctorFixReport(command: DoctorCommand): DoctorFixReport {
        requireProjectTemplate(command.templateId)
        val before = buildDoctorReport(command.configPath)
        val plan = doctorFixPlan(command)

        if (command.dryRun) {
            val next = doctorFixNextCommands(command, status = "planned")
            return DoctorFixReport(
                schema = DOCTOR_FIX_SCHEMA,
                version = KAIOS_VERSION,
                cwd = workingDir.toString(),
                status = "planned",
                dryRun = true,
                requestedTemplate = command.templateId,
                config = command.configPath.toString(),
                before = before,
                plan = plan,
                setup = null,
                after = null,
                errors = emptyList(),
                next = next,
                nextActions = nextActions(next),
            )
        }

        val setupCommand = SetupCommand(
            configPath = command.configPath,
            force = command.force,
            templateId = command.templateId,
            writeCi = command.writeCi,
            format = SetupFormat.Json,
        )
        val setup = buildSetupReport(setupCommand)
        val after = buildDoctorReport(command.configPath)
        val errors = buildList {
            if (!setup.validation.valid) addAll(setup.validation.errors.ifEmpty { listOf("project config is still invalid.") })
            if (after.summary.failed > 0) add("doctor still reports ${after.summary.failed} failed check(s).")
        }
        val status = if (errors.isEmpty()) "fixed" else "failed"
        val next = doctorFixNextCommands(command, status)

        return DoctorFixReport(
            schema = DOCTOR_FIX_SCHEMA,
            version = KAIOS_VERSION,
            cwd = workingDir.toString(),
            status = status,
            dryRun = false,
            requestedTemplate = command.templateId,
            config = command.configPath.toString(),
            before = before,
            plan = plan,
            setup = setup,
            after = after,
            errors = errors,
            next = next,
            nextActions = nextActions(next),
        )
    }

    private fun doctorFixPlan(command: DoctorCommand): DoctorFixPlan {
        val configAction = plannedSetupFileAction(command.configPath, command.force)
        val ciPath = defaultCiWorkflowPath()
        val writes = buildList {
            add(
                DoctorFixPlannedWrite(
                    id = "config",
                    path = displayPath(command.configPath),
                    action = configAction,
                    reason = "Project workflow used by run, gate, config validation, and support diagnostics.",
                ),
            )
            add(
                if (command.writeCi) {
                    DoctorFixPlannedWrite(
                        id = "ci",
                        path = displayPath(ciPath),
                        action = plannedSetupFileAction(ciPath, command.force),
                        reason = "Optional no-key GitHub Actions Agent Gate.",
                    )
                } else {
                    DoctorFixPlannedWrite(
                        id = "ci",
                        path = null,
                        action = SetupFileAction.Skipped.id,
                        reason = "Pass --ci when you want doctor --fix to write the GitHub Actions Agent Gate.",
                    )
                },
            )
        }
        val commands = buildList {
            add(doctorFixCommand(command.configPath, command.templateId, command.writeCi, command.force))
            add(setupCommandFor(command.configPath, command.templateId, command.writeCi, command.force))
            add("kaios config validate --config ${displayPath(command.configPath)} --json")
            if (command.writeCi) add(verifyEvidenceCommand(command.configPath))
        }.distinct()
        val notes = buildList {
            add("doctor --fix reuses the same project setup contract as kaios setup.")
            add("Existing config and CI files are kept unless --force is passed.")
            if (command.dryRun) {
                add("Dry run previews project workflow and CI writes without generating those files.")
            }
        }
        return DoctorFixPlan(
            dryRun = command.dryRun,
            writeCi = command.writeCi,
            force = command.force,
            writes = writes,
            commands = commands,
            notes = notes,
        )
    }

    private fun plannedSetupFileAction(path: Path, force: Boolean): String =
        when {
            path.exists() && force -> SetupFileAction.Overwritten.id
            path.exists() -> SetupFileAction.Existing.id
            else -> SetupFileAction.Created.id
        }

    private fun doctorFixNextCommands(command: DoctorCommand, status: String): List<String> =
        buildList {
            when (status) {
                "planned" -> add(doctorFixCommand(command.configPath, command.templateId, command.writeCi, command.force))
                "fixed" -> {
                    add("kaios config validate --config ${displayPath(command.configPath)} --json")
                    add(verifyEvidenceCommand(command.configPath))
                    if (command.writeCi) {
                        add("git add ${displayPath(command.configPath)} ${displayPath(defaultCiWorkflowPath())}")
                    }
                    add(doctorJsonCommand(command.configPath))
                }
                else -> {
                    add(doctorFixCommand(command.configPath, command.templateId, command.writeCi, force = true, dryRun = true))
                    add(doctorFixCommand(command.configPath, command.templateId, command.writeCi, force = true))
                    addAll(configRecoveryCommands(command.configPath))
                    add(bugReportCommand(command.configPath))
                }
            }
        }.distinct()

    private fun doctorFixCommand(
        configPath: Path,
        templateId: String = "research",
        writeCi: Boolean = false,
        force: Boolean = false,
        dryRun: Boolean = false,
    ): String =
        buildString {
            append("kaios doctor --fix")
            if (dryRun) append(" --dry-run")
            if (templateId != "research") append(" --template $templateId")
            if (configPath.toAbsolutePath().normalize() != defaultConfigPath().toAbsolutePath().normalize()) {
                append(" --config ${displayPath(configPath)}")
            }
            if (writeCi) append(" --ci")
            if (force) append(" --force")
        }

    private fun setupCommandFor(configPath: Path, templateId: String, writeCi: Boolean, force: Boolean): String =
        buildString {
            append("kaios setup")
            if (templateId != "research") append(" --template $templateId")
            if (configPath.toAbsolutePath().normalize() != defaultConfigPath().toAbsolutePath().normalize()) {
                append(" --config ${displayPath(configPath)}")
            }
            if (writeCi) append(" --ci")
            if (force) append(" --force")
        }

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
        out.println("config: ${report.config}")
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

    private fun renderDoctorFixText(report: DoctorFixReport, out: PrintStream) {
        out.println("KAI OS doctor fix")
        out.println("schema: ${report.schema}")
        out.println("version: ${report.version}")
        out.println("cwd: ${report.cwd}")
        out.println("status: ${report.status}")
        out.println("dry_run: ${report.dryRun}")
        out.println("config: ${report.config}")
        out.println("requested_template: ${report.requestedTemplate}")
        out.println("before: ${doctorSummaryText(report.before.summary)}")
        out.println()
        out.println("plan:")
        out.println("  write_ci: ${report.plan.writeCi}")
        out.println("  force: ${report.plan.force}")
        report.plan.writes.forEach { write ->
            out.println("  ${write.id}: ${write.action}${write.path?.let { " ($it)" }.orEmpty()}")
        }
        out.println("  commands:")
        report.plan.commands.forEach { command -> out.println("    $command") }
        if (report.setup != null) {
            out.println()
            out.println("setup: ${if (report.setup.validation.valid) "ready" else "failed"} config=${report.setup.config.action} ci=${report.setup.ci.action}")
        }
        if (report.after != null) {
            out.println("after: ${doctorSummaryText(report.after.summary)}")
        }
        if (report.errors.isNotEmpty()) {
            out.println()
            out.println("errors:")
            report.errors.forEach { error -> out.println("  - ${singleLine(error)}") }
        }
        if (report.plan.notes.isNotEmpty()) {
            out.println()
            out.println("notes:")
            report.plan.notes.forEach { note -> out.println("  - $note") }
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
        val runIdText = args.firstOrNull() ?: "latest"
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
        out.println("  kaios replay --file $capsulePath")
        out.println("  kaios capsule ${capsule.run.runId} --check")
        out.println("  kaios trace ${capsule.run.runId} --check")
    }

    private fun replayCapsule(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseReplayCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "replay", error.message)
        }

        val capsule = runCatching { loadRunCapsule(command.inputPath) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val report = buildRunReplayReport(capsule, command.inputPath)

        if (command.format == ReplayFormat.Json) {
            out.println(CAPSULE_JSON.encodeToString(report))
            return if (report.valid) 0 else 2
        }

        val stream = if (report.valid) out else err
        printRunReplaySummary(report, stream)
        return if (report.valid) 0 else 2
    }

    private fun buildRunReplayReport(capsule: RunCapsule, source: Path): RunReplayReport {
        val capsuleIssues = validateRunCapsule(capsule)
        val rebuiltTrace = buildProcessTrace(capsule.snapshot)
        val rebuiltTraceSha256 = sha256Hex(CAPSULE_JSON.encodeToString(rebuiltTrace))
        val embeddedTraceSha256 = sha256Hex(CAPSULE_JSON.encodeToString(capsule.trace))
        val embeddedSnapshotSha256 = capsule.provenance.embeddedSnapshotSha256
        val embeddedSnapshotHashMatches = embeddedSnapshotSha256 == sha256Hex(CAPSULE_JSON.encodeToString(capsule.snapshot))
        val traceHashMatches = capsule.provenance.traceSha256 == embeddedTraceSha256
        val rebuiltTraceMatchesEmbedded = rebuiltTrace == capsule.trace
        val savedSnapshotHashChecked = Paths.get(capsule.provenance.snapshotPath).exists()

        val replayIssues = buildList {
            if (!rebuiltTraceMatchesEmbedded) {
                add("replay.trace must match the trace rebuilt from the embedded snapshot.")
            }
        }
        val issues = (capsuleIssues + replayIssues).distinct()

        return RunReplayReport(
            schema = RUN_REPLAY_SCHEMA,
            version = KAIOS_VERSION,
            replayedAt = Instant.now().toString(),
            source = source.toAbsolutePath().normalize().toString(),
            capsuleSchema = capsule.schema,
            run = capsule.run,
            valid = issues.isEmpty(),
            deterministic = rebuiltTraceMatchesEmbedded && traceHashMatches && capsuleIssues.isEmpty(),
            issues = issues,
            checks = RunReplayChecks(
                capsuleContract = capsuleIssues.isEmpty(),
                embeddedSnapshotHash = embeddedSnapshotHashMatches,
                traceHash = traceHashMatches,
                rebuiltTraceMatchesEmbedded = rebuiltTraceMatchesEmbedded,
                savedSnapshotHashChecked = savedSnapshotHashChecked,
            ),
            provenance = RunReplayProvenance(
                snapshotSha256 = capsule.provenance.snapshotSha256,
                embeddedSnapshotSha256 = embeddedSnapshotSha256,
                traceSha256 = capsule.provenance.traceSha256,
                rebuiltTraceSha256 = rebuiltTraceSha256,
            ),
            metrics = rebuiltTrace.metrics,
            path = rebuiltTrace.path,
        )
    }

    private fun printRunReplaySummary(report: RunReplayReport, out: PrintStream) {
        out.println("KAI CAPSULE REPLAY")
        out.println("schema: ${report.schema}")
        out.println("capsule: ${report.source}")
        out.println("run: ${report.run.runId}")
        out.println("status: ${if (report.valid) "valid" else "invalid"}")
        out.println("deterministic: ${report.deterministic}")
        out.println("snapshot_sha256: ${report.provenance.snapshotSha256}")
        report.provenance.embeddedSnapshotSha256?.let { out.println("embedded_snapshot_sha256: $it") }
        out.println("trace_sha256: ${report.provenance.traceSha256}")
        out.println("rebuilt_trace_sha256: ${report.provenance.rebuiltTraceSha256}")
        out.println("processes: ${report.metrics.processCount}")
        out.println("events: ${report.metrics.eventCount}")
        val pathText = if (report.path.isEmpty()) "<input>" else "<input> -> ${report.path.joinToString(" -> ")}"
        out.println("path: $pathText")
        if (report.issues.isNotEmpty()) {
            out.println("issues:")
            report.issues.forEach { issue -> out.println("  - $issue") }
        }
    }

    private fun diffCapsules(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseDiffCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "diff", error.message)
        }

        val left = runCatching { loadRunCapsule(command.leftPath) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val right = runCatching { loadRunCapsule(command.rightPath) }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val report = buildRunDiffReport(left, command.leftPath, right, command.rightPath)
        val exitCode = when {
            !report.valid -> 2
            command.check && !report.same -> 1
            else -> 0
        }

        if (command.format == DiffFormat.Json) {
            out.println(CAPSULE_JSON.encodeToString(report))
            return exitCode
        }

        val stream = if (exitCode == 0) out else err
        printRunDiffSummary(report, stream)
        return exitCode
    }

    private fun evidenceRun(args: List<String>, out: PrintStream, err: PrintStream): Int {
        val command = runCatching { parseEvidenceCommand(args) }.getOrElse { error ->
            return printCommandUsageError(err, "evidence", error.message)
        }
        val runId = runCatching { resolveRunId(command.runIdText) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }
        val snapshot = runCatching { snapshotStore.load(runId) }.getOrElse {
            return printSnapshotLoadError(err, it)
        }
        val report = runCatching {
            buildAndWriteRunEvidence(
                snapshot = snapshot,
                outputPath = command.outputPath ?: defaultCapsulePath(runId),
                baselinePath = command.baselinePath,
                forceOutput = command.forceOutput,
            )
        }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val exitCode = when {
            !report.valid -> 2
            command.check && report.diff.same == false -> 1
            else -> 0
        }

        if (command.format == EvidenceFormat.Json) {
            out.println(CAPSULE_JSON.encodeToString(report))
            return exitCode
        }

        val stream = if (exitCode == 0) out else err
        printRunEvidenceSummary(report, stream)
        return exitCode
    }

    private fun buildAndWriteRunEvidence(
        snapshot: StoredRunSnapshot,
        outputPath: Path,
        baselinePath: Path?,
        forceOutput: Boolean,
    ): RunEvidenceReport {
        if (baselinePath?.toAbsolutePath()?.normalize() == outputPath.toAbsolutePath().normalize()) {
            error("Baseline capsule and output capsule must be different files.")
        }
        val capsule = buildRunCapsule(snapshot)
        val renderedCapsule = CAPSULE_JSON.encodeToString(capsule)
        val writtenCapsulePath = writeTextOutput("$renderedCapsule\n", outputPath, forceOutput)
        val replay = buildRunReplayReport(capsule, writtenCapsulePath)
        val diff = baselinePath?.let { baseline ->
            buildRunDiffReport(loadRunCapsule(baseline), baseline, capsule, writtenCapsulePath)
        }
        return buildRunEvidenceReport(
            capsule = capsule,
            capsulePath = writtenCapsulePath,
            replay = replay,
            diff = diff,
            baselinePath = baselinePath,
        )
    }

    private fun buildRunEvidenceReport(
        capsule: RunCapsule,
        capsulePath: Path,
        replay: RunReplayReport,
        diff: RunDiffReport?,
        baselinePath: Path?,
    ): RunEvidenceReport {
        val capsuleIssues = validateRunCapsule(capsule)
        val diffStatus = when {
            diff == null -> "skipped"
            !diff.valid -> "invalid"
            diff.same -> "same"
            else -> "different"
        }
        val valid = capsuleIssues.isEmpty() && replay.valid && (diff?.valid ?: true)
        val status = when {
            !valid -> "invalid"
            diffStatus == "different" -> "different"
            else -> "valid"
        }
        val absoluteCapsulePath = capsulePath.toAbsolutePath().normalize()
        val absoluteBaselinePath = baselinePath?.toAbsolutePath()?.normalize()
        val next = evidenceNextCommands(absoluteCapsulePath, absoluteBaselinePath, capsule.run.runId)
        return RunEvidenceReport(
            schema = RUN_EVIDENCE_SCHEMA,
            version = KAIOS_VERSION,
            generatedAt = Instant.now().toString(),
            runId = capsule.run.runId,
            capsulePath = absoluteCapsulePath.toString(),
            valid = valid,
            status = status,
            capsule = EvidenceCapsuleStatus(
                status = if (capsuleIssues.isEmpty()) "valid" else "invalid",
                valid = capsuleIssues.isEmpty(),
                schema = capsule.schema,
                snapshotSha256 = capsule.provenance.snapshotSha256,
                traceSha256 = capsule.provenance.traceSha256,
                issues = capsuleIssues,
            ),
            replay = EvidenceReplayStatus(
                status = if (replay.valid) "valid" else "invalid",
                valid = replay.valid,
                deterministic = replay.deterministic,
                rebuiltTraceSha256 = replay.provenance.rebuiltTraceSha256,
                issues = replay.issues,
            ),
            diff = EvidenceDiffStatus(
                status = diffStatus,
                valid = diff?.valid ?: true,
                same = diff?.same,
                baselinePath = absoluteBaselinePath?.toString(),
                baselineRunId = diff?.left?.run?.runId,
                currentRunId = diff?.right?.run?.runId,
                leftStableSha256 = diff?.left?.stableSha256,
                rightStableSha256 = diff?.right?.stableSha256,
                differences = diff?.differences?.size ?: 0,
                changes = diff?.differences?.take(EVIDENCE_DIFF_CHANGE_LIMIT) ?: emptyList(),
                issues = diff?.issues ?: emptyList(),
            ),
            next = next,
            nextActions = nextActions(next),
        )
    }

    private fun evidenceNextCommands(capsulePath: Path, baselinePath: Path?, runId: String): List<String> =
        buildList {
            add("kaios capsule --file ${displayPath(capsulePath)} --check")
            add("kaios replay --file ${displayPath(capsulePath)}")
            if (baselinePath == null) {
                add("kaios evidence $runId --baseline artifacts/baseline.capsule.json --check --force")
            } else {
                add("kaios diff ${displayPath(baselinePath)} ${displayPath(capsulePath)} --check")
            }
            add("kaios ps $runId")
        }.distinct()

    private fun printRunEvidenceSummary(report: RunEvidenceReport, out: PrintStream) {
        out.println("KAI EVIDENCE")
        out.println("schema: ${report.schema}")
        out.println("run: ${report.runId}")
        out.println("status: ${report.status}")
        out.println("capsule: ${report.capsulePath}")
        out.println("capsule_status: ${report.capsule.status}")
        out.println("replay_status: ${report.replay.status}")
        out.println("replay_deterministic: ${report.replay.deterministic}")
        out.println("diff_status: ${report.diff.status}")
        report.diff.baselinePath?.let { out.println("baseline: $it") }
        report.diff.leftStableSha256?.let { out.println("baseline_stable_sha256: $it") }
        report.diff.rightStableSha256?.let { out.println("current_stable_sha256: $it") }
        if (report.diff.status != "skipped") {
            out.println("differences: ${report.diff.differences}")
            if (report.diff.changes.isNotEmpty()) {
                out.println("changes:")
                report.diff.changes.forEach { change ->
                    out.println("  - ${change.field}: ${abbreviate(singleLine(change.left), 80)} -> ${abbreviate(singleLine(change.right), 80)}")
                }
            }
        }
        val issues = (report.capsule.issues.map { "capsule: $it" } +
            report.replay.issues.map { "replay: $it" } +
            report.diff.issues.map { "diff: $it" }).distinct()
        if (issues.isNotEmpty()) {
            out.println("issues:")
            issues.forEach { issue -> out.println("  - $issue") }
        }
        out.println("next:")
        report.next.forEach { command -> out.println("  $command") }
    }

    private fun buildRunDiffReport(
        left: RunCapsule,
        leftSource: Path,
        right: RunCapsule,
        rightSource: Path,
    ): RunDiffReport {
        val leftReplay = buildRunReplayReport(left, leftSource)
        val rightReplay = buildRunReplayReport(right, rightSource)
        val issues = (
            leftReplay.issues.map { issue -> "left: $issue" } +
                rightReplay.issues.map { issue -> "right: $issue" }
            ).distinct()

        val leftTrace = buildProcessTrace(left.snapshot)
        val rightTrace = buildProcessTrace(right.snapshot)
        val leftSignature = stableRunSignature(left, leftTrace)
        val rightSignature = stableRunSignature(right, rightTrace)
        val differences = if (issues.isEmpty()) compareStableSignatures(leftSignature, rightSignature) else emptyList()
        val same = issues.isEmpty() && differences.isEmpty()

        return RunDiffReport(
            schema = RUN_DIFF_SCHEMA,
            version = KAIOS_VERSION,
            comparedAt = Instant.now().toString(),
            result = when {
                issues.isNotEmpty() -> "invalid"
                same -> "same"
                else -> "different"
            },
            valid = issues.isEmpty(),
            same = same,
            left = RunDiffSide(
                source = leftSource.toAbsolutePath().normalize().toString(),
                run = left.run,
                deterministic = leftReplay.deterministic,
                stableSha256 = sha256Hex(CAPSULE_JSON.encodeToString(leftSignature)),
                issues = leftReplay.issues,
            ),
            right = RunDiffSide(
                source = rightSource.toAbsolutePath().normalize().toString(),
                run = right.run,
                deterministic = rightReplay.deterministic,
                stableSha256 = sha256Hex(CAPSULE_JSON.encodeToString(rightSignature)),
                issues = rightReplay.issues,
            ),
            checks = RunDiffChecks(
                leftCapsule = leftReplay.checks.capsuleContract,
                rightCapsule = rightReplay.checks.capsuleContract,
                leftReplay = leftReplay.valid,
                rightReplay = rightReplay.valid,
            ),
            metricsDelta = RunDiffMetricsDelta(
                processCount = rightSignature.metrics.processCount - leftSignature.metrics.processCount,
                tokenTotal = rightSignature.metrics.tokenTotal - leftSignature.metrics.tokenTotal,
                inputTokens = rightSignature.metrics.inputTokens - leftSignature.metrics.inputTokens,
                outputTokens = rightSignature.metrics.outputTokens - leftSignature.metrics.outputTokens,
                contextBytes = rightSignature.metrics.contextBytes - leftSignature.metrics.contextBytes,
                syscallCount = rightSignature.metrics.syscallCount - leftSignature.metrics.syscallCount,
                eventCount = rightSignature.metrics.eventCount - leftSignature.metrics.eventCount,
            ),
            differences = differences,
            issues = issues,
        )
    }

    private fun stableRunSignature(capsule: RunCapsule, trace: ProcessTrace): StableRunSignature =
        StableRunSignature(
            workflowName = capsule.snapshot.workflowName,
            task = capsule.snapshot.task,
            success = capsule.snapshot.success,
            finalOutputSha256 = sha256Hex(capsule.snapshot.finalOutput),
            metrics = StableRunMetrics(
                processCount = trace.metrics.processCount,
                tokenTotal = trace.metrics.tokenTotal,
                inputTokens = trace.metrics.inputTokens,
                outputTokens = trace.metrics.outputTokens,
                contextBytes = trace.metrics.contextBytes,
                syscallCount = trace.metrics.syscallCount,
                eventCount = trace.metrics.eventCount,
            ),
            path = trace.path,
            processes = trace.processes.map { process ->
                StableRunProcess(
                    agent = process.agent,
                    state = process.state,
                    tokens = process.tokens,
                    inputTokens = process.inputTokens,
                    outputTokens = process.outputTokens,
                    contextBytes = process.contextBytes,
                    syscallCount = process.syscallCount,
                    failure = process.failure,
                )
            },
            eventCounts = trace.eventCounts.toSortedMap(),
        )

    private fun compareStableSignatures(left: StableRunSignature, right: StableRunSignature): List<RunDiffDifference> =
        buildList {
            addDifference("workflowName", left.workflowName, right.workflowName)
            addDifference("task", left.task, right.task)
            addDifference("success", left.success.toString(), right.success.toString())
            addDifference("finalOutputSha256", left.finalOutputSha256, right.finalOutputSha256)
            addDifference("metrics.processCount", left.metrics.processCount.toString(), right.metrics.processCount.toString())
            addDifference("metrics.tokenTotal", left.metrics.tokenTotal.toString(), right.metrics.tokenTotal.toString())
            addDifference("metrics.inputTokens", left.metrics.inputTokens.toString(), right.metrics.inputTokens.toString())
            addDifference("metrics.outputTokens", left.metrics.outputTokens.toString(), right.metrics.outputTokens.toString())
            addDifference("metrics.contextBytes", left.metrics.contextBytes.toString(), right.metrics.contextBytes.toString())
            addDifference("metrics.syscallCount", left.metrics.syscallCount.toString(), right.metrics.syscallCount.toString())
            addDifference("metrics.eventCount", left.metrics.eventCount.toString(), right.metrics.eventCount.toString())
            addDifference("path", left.path.displayStringList(), right.path.displayStringList())
            addDifference("processes", left.processes.displayProcessList(), right.processes.displayProcessList())
            addDifference("eventCounts", left.eventCounts.displayMap(), right.eventCounts.displayMap())
        }

    private fun MutableList<RunDiffDifference>.addDifference(field: String, left: String, right: String) {
        if (left != right) add(RunDiffDifference(field = field, left = left, right = right))
    }

    private fun List<StableRunProcess>.displayProcessList(): String =
        joinToString(prefix = "[", postfix = "]") { process ->
            "${process.agent}:${process.state}:tokens=${process.tokens}:input=${process.inputTokens}:output=${process.outputTokens}:context=${process.contextBytes}:syscalls=${process.syscallCount}:failure=${process.failure ?: "-"}"
        }

    private fun List<String>.displayStringList(): String =
        joinToString(prefix = "[", postfix = "]")

    private fun Map<String, Int>.displayMap(): String =
        entries.joinToString(prefix = "{", postfix = "}") { (key, value) -> "$key=$value" }

    private fun printRunDiffSummary(report: RunDiffReport, out: PrintStream) {
        out.println("KAI CAPSULE DIFF")
        out.println("schema: ${report.schema}")
        out.println("status: ${report.result}")
        out.println("left: ${report.left.source}")
        out.println("left_run: ${report.left.run.runId}")
        out.println("right: ${report.right.source}")
        out.println("right_run: ${report.right.run.runId}")
        out.println("same: ${report.same}")
        out.println("left_stable_sha256: ${report.left.stableSha256}")
        out.println("right_stable_sha256: ${report.right.stableSha256}")
        out.println("metrics_delta:")
        out.println("  processes: ${formatDelta(report.metricsDelta.processCount)}")
        out.println("  tokens: ${formatDelta(report.metricsDelta.tokenTotal)}")
        out.println("  input_tokens: ${formatDelta(report.metricsDelta.inputTokens)}")
        out.println("  output_tokens: ${formatDelta(report.metricsDelta.outputTokens)}")
        out.println("  context_bytes: ${formatDelta(report.metricsDelta.contextBytes)}")
        out.println("  syscalls: ${formatDelta(report.metricsDelta.syscallCount)}")
        out.println("  events: ${formatDelta(report.metricsDelta.eventCount)}")
        if (report.issues.isNotEmpty()) {
            out.println("issues:")
            report.issues.forEach { issue -> out.println("  - $issue") }
        }
        if (report.differences.isEmpty()) {
            out.println("differences: none")
        } else {
            out.println("differences:")
            report.differences.forEach { difference ->
                out.println("  - ${difference.field}: ${difference.left} -> ${difference.right}")
            }
        }
    }

    private fun formatDelta(value: Int): String =
        if (value > 0) "+$value" else value.toString()

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
                    "kaios replay --file <capsule.json>",
                    "kaios capsule --file <capsule.json> --check",
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

            Product model:
              Agent    = Process
              Workflow = Scheduler
              Tool     = Syscall
              Memory   = Process state

            Use KAI OS when:
              - CI needs a no-key Agent Gate before real model credentials.
              - A multi-agent run needs PID/state/token/context/syscall observability.
              - A run needs portable evidence for review, replay, support, or baseline drift.

            Three-step product path:
              1. kaios quickstart --dry-run
              2. kaios quickstart
              3. kaios ps && kaios inspect

            Need the next workspace-aware move?
              kaios next

            Quick start shortcuts:
              kaios quickstart --dry-run
              kaios quickstart

            Manual path (3 steps):
              kaios demo
              kaios setup --ci
              kaios gate

            Command groups:
              Setup:
                kaios next [--config kaios.json]
                kaios quickstart [--dry-run]
                kaios setup [--ci]
                kaios gate [--config kaios.json]
                kaios verify [--config kaios.json] [--evidence]
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
                kaios ps [latest]
                kaios inspect [latest]
                kaios trace [latest] [--format text|json] [--out trace.json] [--check]
                kaios capsule [latest] [--json] [--out capsule.json] [--check]
                kaios replay --file capsule.json [--json]
                kaios diff baseline.capsule.json current.capsule.json [--check]
                kaios evidence [latest] [--out capsule.json] [--baseline baseline.capsule.json] [--check]
                kaios report [latest]
                kaios export [latest] [--out artifact.md]
                kaios doctor [--config kaios.json] [--fix] [--dry-run]
                kaios bug-report [--config kaios.json] [--out report.md]
                kaios --version
                kaios help <command>

              Common aliases:
                kaios start [--no-ci]  -> kaios quickstart
                kaios status           -> kaios doctor
                kaios ls               -> kaios runs
                kaios proc             -> kaios ps
                kaios audit            -> kaios evidence
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

    private fun defaultVerifyEvidencePath(): Path =
        workingDir.resolve("artifacts").resolve("kaios-run.capsule.json").normalize()

    private fun defaultQuickstartEvidencePath(): Path =
        artifactRoot.resolve("kaios-quickstart.capsule.json").normalize()

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

    private fun appendVerifySummary(report: VerifyReport, title: String, command: VerifyCommand, path: Path): Path {
        path.parent?.let { Files.createDirectories(it) }
        val prefix = if (path.exists() && Files.size(path) > 0L) "\n" else ""
        Files.writeString(
            path,
            "$prefix${renderVerifySummaryMarkdown(report, title, command)}\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND,
        )
        return path
    }

    private fun renderVerifySummaryMarkdown(report: VerifyReport, title: String, command: VerifyCommand): String = buildString {
        val diagnosis = report.diagnosis
        val summaryStatus = diagnosis.status
        val fixFirst = diagnosis.fixFirst?.command
        appendLine("## $title")
        appendLine()
        appendLine("### Verdict")
        appendLine()
        appendLine(diagnosis.verdict)
        if (summaryStatus != "ready") {
            val reasons = diagnosis.reasons
            if (reasons.isNotEmpty()) {
                appendLine()
                appendLine("### Why It Failed")
                reasons.forEach { reason -> appendLine("- ${markdownCell(reason)}") }
            }
            if (fixFirst != null) {
                appendLine()
                appendLine("### Fix First")
                appendLine()
                appendLine("Run:")
                appendLine()
                appendLine("```bash")
                appendLine(fixFirst)
                appendLine("```")
                appendLine()
                appendLine(nextActionReason(nextActionId(fixFirst)))
            }
            val changes = diagnosis.diffChanges
            if (changes.isNotEmpty()) {
                appendLine()
                appendLine("### What Changed")
                appendLine()
                appendLine("| Field | Baseline | Current |")
                appendLine("| --- | --- | --- |")
                changes.forEach { change ->
                    appendLine(
                        "| `${markdownCell(change.field)}` | `${markdownCell(abbreviate(singleLine(change.left), 96))}` | `${markdownCell(abbreviate(singleLine(change.right), 96))}` |",
                    )
                }
                val total = report.evidence?.diff?.differences ?: changes.size
                if (total > changes.size) {
                    appendLine()
                    appendLine("_Showing ${changes.size} of $total stable runtime difference(s)._")
                }
            }
        }
        appendLine()
        appendLine("| Check | Status | Detail |")
        appendLine("| --- | --- | --- |")
        appendLine("| Agent Gate | `$summaryStatus` | `${report.schema}` `${report.version}` |")
        appendLine("| Readiness | `${report.status}` | `${report.schema}` `${report.version}` |")
        appendLine("| Doctor | `${doctorSummaryText(report.doctor.summary)}` | `${report.doctor.summary.failed}` failed, `${report.doctor.summary.warnings}` warning(s) |")
        appendLine("| Config | `${if (report.config.valid) "valid" else "invalid"}` | `${markdownCell(report.config.config)}` workflow `${report.config.workflowName ?: "-"}` |")
        val run = report.run
        if (run == null) {
            appendLine("| Run | `skipped` | no smoke workflow was executed |")
        } else {
            appendLine(
                "| Run | `${if (run.success) "passed" else "failed"}` | `${run.runId}` workflow `${markdownCell(run.workflowName)}` |",
            )
            appendLine("| Process metrics | `recorded` | `${run.processCount}` processes, `${run.tokenTotal}` tokens, `${run.syscallCount}` syscalls, `${run.contextBytes}` context bytes, `${run.durationMillis}` ms |")
        }
        val trace = report.trace
        if (trace == null) {
            appendLine("| Process trace | `skipped` | no trace was produced |")
        } else {
            appendLine("| Process trace | `${if (trace.valid) "valid" else "invalid"}` | `${trace.schema}`, `${trace.processCount}` processes, `${trace.eventCount}` events |")
        }
        val evidence = report.evidence
        if (evidence == null) {
            appendLine("| Evidence | `skipped` | run `kaios gate` or `kaios verify --evidence --force` to write a capsule |")
        } else {
            appendLine("| Evidence | `${evidence.status}` | capsule `${markdownCell(evidence.capsulePath)}`, replay `${evidence.replay.status}`, diff `${evidence.diff.status}` |")
        }
        val warnings = doctorWarnings(report.doctor)
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("### Warnings")
            warnings.forEach { warning -> appendLine("- ${markdownCell(singleLine(warning))}") }
        }
        if (report.errors.isNotEmpty()) {
            appendLine()
            appendLine("### Errors")
            report.errors.forEach { error -> appendLine("- ${markdownCell(singleLine(error))}") }
        }
        if (report.next.isNotEmpty()) {
            appendLine()
            appendLine("### Next Commands")
            report.next.forEach { command -> appendLine("- `$command`") }
        }
    }.trimEnd()

    private fun verifyDiagnosis(report: VerifyReport, command: VerifyCommand): VerifyDiagnosis {
        val status = verifySummaryStatus(report, command)
        val fixFirst = if (status == "ready") null else verifySummaryFixCommand(report, command)?.let(::nextAction)
        return VerifyDiagnosis(
            status = status,
            verdict = verifySummaryVerdict(status),
            reasons = verifySummaryFailureReasons(report, command),
            fixFirst = fixFirst,
            diffChanges = verifySummaryDiffChanges(report),
        )
    }

    private fun verifySummaryStatus(report: VerifyReport, command: VerifyCommand): String =
        when {
            report.status != "ready" -> "failed"
            report.evidence?.valid == false -> "failed"
            command.evidenceCheck && report.evidence?.diff?.same == false -> "different"
            else -> "ready"
        }

    private fun verifySummaryVerdict(status: String): String =
        when (status) {
            "ready" -> "Ready. Agent Gate passed and the run is inspectable."
            "different" -> "Different. Agent Gate passed, but the baseline check found behavior changes."
            else -> "Failed. Fix the first blocking issue below, then rerun `kaios gate`."
        }

    private fun verifySummaryFailureReasons(report: VerifyReport, command: VerifyCommand): List<String> =
        buildList {
            if (report.doctor.summary.failed > 0) {
                report.doctor.checks
                    .filter { check -> check.status == DoctorStatus.FAIL.name }
                    .filterNot { check -> !report.config.valid && check.name == "project config" }
                    .forEach { check -> add("${check.name}: ${singleLine(check.detail)}") }
            }
            if (!report.config.valid) addAll(report.config.errors)
            report.trace?.issues?.forEach { issue -> add("trace: $issue") }
            report.evidence?.let { evidence ->
                if (!evidence.valid) {
                    evidence.capsule.issues.forEach { issue -> add("capsule: $issue") }
                    evidence.replay.issues.forEach { issue -> add("replay: $issue") }
                    evidence.diff.issues.forEach { issue -> add("diff: $issue") }
                }
                if (command.evidenceCheck && evidence.diff.same == false) {
                    add("baseline diff: ${evidence.diff.differences} stable runtime difference(s) found.")
                }
            }
            report.errors
                .filterNot { error -> error.startsWith("doctor failed with ") }
                .filterNot { error -> report.config.errors.any { configError -> singleLine(configError) == singleLine(error) } }
                .forEach(::add)
        }.map(::singleLine).filter { it.isNotBlank() }.distinct()

    private fun verifySummaryFixCommand(report: VerifyReport, command: VerifyCommand): String? {
        val evidence = report.evidence
        if (command.evidenceCheck && evidence?.diff?.same == false) {
            return evidence.next.firstOrNull { next -> next.startsWith("kaios diff ") }
        }
        if (!report.config.valid) {
            return report.next.firstOrNull { next ->
                nextActionId(next) == "repair-project"
            } ?: report.next.firstOrNull { next ->
                nextActionId(next) == "regenerate-config" || nextActionId(next) == "setup-project"
            }
        }
        return report.next.firstOrNull()
    }

    private fun verifySummaryDiffChanges(report: VerifyReport): List<RunDiffDifference> =
        report.evidence
            ?.diff
            ?.takeIf { diff -> diff.same == false }
            ?.changes
            .orEmpty()

    private fun markdownCell(value: String): String =
        singleLine(value).replace("|", "\\|")

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
        val artifactPaths = CI_AGENT_GATE_ARTIFACT_PATHS.joinToString("\n") { "            $it" }
        val workflow = """
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
                    run: |
                      set -euo pipefail
                      mkdir -p artifacts
                      kaios gate --config $config --summary-out "${'$'}GITHUB_STEP_SUMMARY" --json | tee artifacts/kaios-verify.json

                  - name: Collect KAI OS support report
                    if: failure()
                    run: |
                      mkdir -p artifacts
                      kaios bug-report --config $config --json --out artifacts/kaios-bug-report.json --force

                  - name: Upload KAI OS artifacts
                    if: always()
                    uses: actions/upload-artifact@v7.0.1
                    with:
                      name: $CI_AGENT_GATE_ARTIFACT_NAME
                      path: |
                        __CI_AGENT_GATE_ARTIFACT_PATHS__
                      if-no-files-found: ignore
        """.trimIndent()
        return workflow.replace("            __CI_AGENT_GATE_ARTIFACT_PATHS__", artifactPaths) + "\n"
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

    private fun parseQuickstartCommand(args: List<String>): QuickstartCommand {
        var force = false
        var writeCi = true
        var dryRun = false
        var format = QuickstartFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--dry-run" || arg == "--plan" -> {
                    dryRun = true
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    force = true
                    index += 1
                }
                arg == "--no-ci" || arg == "--local" -> {
                    writeCi = false
                    index += 1
                }
                arg == "--json" -> {
                    format = QuickstartFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseQuickstartFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseQuickstartFormat(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown quickstart option '$arg'.")
                else -> error("Unexpected quickstart argument '$arg'.")
            }
        }

        return QuickstartCommand(force = force, writeCi = writeCi, dryRun = dryRun, format = format)
    }

    private fun parseQuickstartFormat(value: String): QuickstartFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> QuickstartFormat.Text
            "json" -> QuickstartFormat.Json
            else -> error("Unknown quickstart format '$value'. Use text or json.")
        }

    private fun parseSetupFormat(value: String): SetupFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> SetupFormat.Text
            "json" -> SetupFormat.Json
            else -> error("Unknown setup format '$value'. Use text or json.")
        }

    private fun parseVerifyCommand(args: List<String>, commandName: String = "verify"): VerifyCommand {
        var configPath = defaultConfigPath()
        var format = VerifyFormat.Text
        var evidenceOutputPath: Path? = null
        var evidenceBaselinePath: Path? = null
        var evidenceDefault = false
        var evidenceCheck = false
        var evidenceForce = false
        var summaryOutputPath: Path? = null
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
                arg == "--evidence" -> {
                    evidenceDefault = true
                    index += 1
                }
                arg == "--evidence-out" || arg == "--evidence-output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    evidenceOutputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--evidence-out=") || arg.startsWith("--evidence-output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    evidenceOutputPath = resolvePath(value)
                    index += 1
                }
                arg == "--baseline" || arg == "--evidence-baseline" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    evidenceBaselinePath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--baseline=") || arg.startsWith("--evidence-baseline=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    evidenceBaselinePath = resolvePath(value)
                    index += 1
                }
                arg == "--check" || arg == "--evidence-check" -> {
                    evidenceCheck = true
                    index += 1
                }
                arg == "--force" || arg == "-f" || arg == "--evidence-force" -> {
                    evidenceForce = true
                    index += 1
                }
                arg == "--summary-out" || arg == "--summary-output" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    summaryOutputPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--summary-out=") || arg.startsWith("--summary-output=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    summaryOutputPath = resolvePath(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown $commandName option '$arg'.")
                else -> error("Unexpected $commandName argument '$arg'.")
            }
        }

        val finalEvidenceOutputPath = when {
            evidenceOutputPath != null -> evidenceOutputPath
            evidenceDefault || evidenceBaselinePath != null -> defaultVerifyEvidencePath()
            else -> null
        }
        if ((evidenceCheck || evidenceForce) && finalEvidenceOutputPath == null && evidenceBaselinePath == null) {
            error("--check and --force require --evidence, --evidence-out, or --baseline.")
        }

        return VerifyCommand(
            configPath = configPath,
            format = format,
            evidenceOutputPath = finalEvidenceOutputPath,
            evidenceBaselinePath = evidenceBaselinePath,
            evidenceCheck = evidenceCheck,
            evidenceForce = evidenceForce,
            summaryOutputPath = summaryOutputPath,
        )
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
        var runIdText: String? = null
        var outputPath: Path? = null
        var force = false
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
                arg.startsWith("-") -> error("Unknown export option '$arg'.")
                runIdText == null -> {
                    runIdText = arg
                    index += 1
                }
                else -> error("Unexpected export argument '$arg'.")
            }
        }

        return ExportCommand(runIdText ?: "latest", outputPath, force)
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

        return TraceCommand(runIdText ?: "latest", format, outputPath, forceOutput, check)
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
            runIdText = runIdText ?: if (inputPath == null) "latest" else null,
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

    private fun parseReplayCommand(args: List<String>): ReplayCommand {
        var inputPath: Path? = null
        var format = ReplayFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = ReplayFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseReplayFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseReplayFormat(value)
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
                arg == "--check" -> {
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown replay option '$arg'.")
                inputPath == null -> {
                    inputPath = resolvePath(arg)
                    index += 1
                }
                else -> error("Unexpected replay argument '$arg'.")
            }
        }

        return ReplayCommand(inputPath ?: error("Capsule file is required."), format)
    }

    private fun parseReplayFormat(value: String): ReplayFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> ReplayFormat.Text
            "json" -> ReplayFormat.Json
            else -> error("Unknown replay format '$value'. Use text or json.")
        }

    private fun parseDiffCommand(args: List<String>): DiffCommand {
        var leftPath: Path? = null
        var rightPath: Path? = null
        var format = DiffFormat.Text
        var check = false
        var index = 0

        fun setPositionalPath(value: String) {
            when {
                leftPath == null -> leftPath = resolvePath(value)
                rightPath == null -> rightPath = resolvePath(value)
                else -> error("Unexpected diff argument '$value'.")
            }
        }

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = DiffFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseDiffFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseDiffFormat(value)
                    index += 1
                }
                arg == "--left" || arg == "--baseline" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    leftPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--left=") || arg.startsWith("--baseline=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    leftPath = resolvePath(value)
                    index += 1
                }
                arg == "--right" || arg == "--current" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    rightPath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--right=") || arg.startsWith("--current=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    rightPath = resolvePath(value)
                    index += 1
                }
                arg == "--check" -> {
                    check = true
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown diff option '$arg'.")
                else -> {
                    setPositionalPath(arg)
                    index += 1
                }
            }
        }

        return DiffCommand(
            leftPath = leftPath ?: error("Left capsule file is required."),
            rightPath = rightPath ?: error("Right capsule file is required."),
            format = format,
            check = check,
        )
    }

    private fun parseDiffFormat(value: String): DiffFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> DiffFormat.Text
            "json" -> DiffFormat.Json
            else -> error("Unknown diff format '$value'. Use text or json.")
        }

    private fun parseEvidenceCommand(args: List<String>): EvidenceCommand {
        var runIdText: String? = null
        var outputPath: Path? = null
        var baselinePath: Path? = null
        var format = EvidenceFormat.Text
        var check = false
        var forceOutput = false
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--json" -> {
                    format = EvidenceFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseEvidenceFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseEvidenceFormat(value)
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
                arg == "--baseline" || arg == "--left" -> {
                    val value = args.getOrNull(index + 1) ?: error("$arg requires a path.")
                    baselinePath = resolvePath(value)
                    index += 2
                }
                arg.startsWith("--baseline=") || arg.startsWith("--left=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "$arg requires a path." }
                    baselinePath = resolvePath(value)
                    index += 1
                }
                arg == "--check" -> {
                    check = true
                    index += 1
                }
                arg == "--force" || arg == "-f" -> {
                    forceOutput = true
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown evidence option '$arg'.")
                runIdText == null -> {
                    runIdText = arg
                    index += 1
                }
                else -> error("Unexpected evidence argument '$arg'.")
            }
        }

        return EvidenceCommand(
            runIdText = runIdText ?: "latest",
            outputPath = outputPath,
            baselinePath = baselinePath,
            format = format,
            check = check,
            forceOutput = forceOutput,
        )
    }

    private fun parseEvidenceFormat(value: String): EvidenceFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> EvidenceFormat.Text
            "json" -> EvidenceFormat.Json
            else -> error("Unknown evidence format '$value'. Use text or json.")
        }

    private fun parseDoctorCommand(args: List<String>): DoctorCommand {
        var configPath = defaultConfigPath()
        var format = DoctorFormat.Text
        var fix = false
        var dryRun = false
        var force = false
        var writeCi = false
        var templateId = "research"
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--fix" -> {
                    fix = true
                    index += 1
                }
                arg == "--dry-run" || arg == "--plan" -> {
                    dryRun = true
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
                    val value = args.getOrNull(index + 1) ?: error("--config requires a path.")
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

        if (!fix && (dryRun || force || writeCi || templateId != "research")) {
            error("--dry-run, --ci, --force, and --template require --fix.")
        }

        return DoctorCommand(
            configPath = configPath,
            format = format,
            fix = fix,
            dryRun = dryRun,
            writeCi = writeCi,
            force = force,
            templateId = templateId,
        )
    }

    private fun parseDoctorFormat(value: String): DoctorFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> DoctorFormat.Text
            "json" -> DoctorFormat.Json
            else -> error("Unknown doctor format '$value'. Use text or json.")
        }

    private fun parseNextCommand(args: List<String>): NextCommand {
        var configPath = defaultConfigPath()
        var format = NextFormat.Text
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("--config requires a path.")
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
                    format = NextFormat.Json
                    index += 1
                }
                arg == "--format" -> {
                    val value = args.getOrNull(index + 1) ?: error("--format requires text or json.")
                    format = parseNextFormat(value)
                    index += 2
                }
                arg.startsWith("--format=") -> {
                    val value = arg.substringAfter("=")
                    require(value.isNotBlank()) { "--format requires text or json." }
                    format = parseNextFormat(value)
                    index += 1
                }
                arg.startsWith("-") -> error("Unknown next option '$arg'.")
                else -> error("Unexpected next argument '$arg'.")
            }
        }

        return NextCommand(configPath, format)
    }

    private fun parseNextFormat(value: String): NextFormat =
        when (value.lowercase().trim()) {
            "text", "plain" -> NextFormat.Text
            "json" -> NextFormat.Json
            else -> error("Unknown next format '$value'. Use text or json.")
        }

    private fun parseBugReportCommand(args: List<String>): BugReportCommand {
        var configPath = defaultConfigPath()
        var outputPath: Path? = null
        var force = false
        var format = BugReportFormat.Markdown
        var index = 0

        while (index < args.size) {
            val arg = args[index]
            when {
                arg == "--config" || arg == "-c" -> {
                    val value = args.getOrNull(index + 1) ?: error("--config requires a path.")
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

        return BugReportCommand(configPath, outputPath, force, format)
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

@Serializable
private data class NextAction(
    val id: String,
    val command: String,
    val reason: String,
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

private data class QuickstartCommand(
    val force: Boolean,
    val writeCi: Boolean,
    val dryRun: Boolean,
    val format: QuickstartFormat,
)

private enum class QuickstartFormat(val id: String) {
    Text("text"),
    Json("json"),
}

@Serializable
private data class QuickstartReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val status: String,
    val plan: QuickstartPlan,
    val demo: DemoRunReport?,
    val setup: SetupReport?,
    val verify: VerifyReport?,
    val errors: List<String>,
    val next: List<String>,
    val nextActions: List<NextAction>,
)

@Serializable
private data class QuickstartPlan(
    val dryRun: Boolean,
    val mode: String,
    val writes: List<QuickstartPlannedWrite>,
    val commands: List<String>,
    val notes: List<String>,
)

@Serializable
private data class QuickstartPlannedWrite(
    val id: String,
    val path: String?,
    val action: String,
    val reason: String,
)

@Serializable
private data class DemoRunReport(
    val runId: String,
    val success: Boolean,
    val snapshot: String,
    val artifact: String,
    val trace: String,
    val processCount: Int,
    val tokenTotal: Int,
    val syscallCount: Int,
    val output: String,
    val processes: List<StoredProcess>,
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
    val ciArtifact: SetupCiArtifact?,
    val doctor: DoctorReport,
    val validation: ConfigValidationReport,
    val next: List<String>,
    val nextActions: List<NextAction>,
)

@Serializable
private data class SetupFileReport(
    val path: String?,
    val action: String,
)

@Serializable
private data class SetupCiArtifact(
    val name: String,
    val paths: List<String>,
    val pushPermissionNote: String = CI_WORKFLOW_PUSH_NOTE,
)

private data class VerifyCommand(
    val configPath: Path,
    val format: VerifyFormat,
    val evidenceOutputPath: Path? = null,
    val evidenceBaselinePath: Path? = null,
    val evidenceCheck: Boolean = false,
    val evidenceForce: Boolean = false,
    val summaryOutputPath: Path? = null,
) {
    val evidenceRequested: Boolean
        get() = evidenceOutputPath != null || evidenceBaselinePath != null
}

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
    val evidence: RunEvidenceReport?,
    val errors: List<String>,
    val next: List<String>,
    val nextActions: List<NextAction>,
    val diagnosis: VerifyDiagnosis,
)

@Serializable
private data class VerifyDiagnosis(
    val status: String,
    val verdict: String,
    val reasons: List<String>,
    val fixFirst: NextAction?,
    val diffChanges: List<RunDiffDifference>,
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

private data class ReplayCommand(
    val inputPath: Path,
    val format: ReplayFormat,
)

private data class DiffCommand(
    val leftPath: Path,
    val rightPath: Path,
    val format: DiffFormat,
    val check: Boolean,
)

private data class EvidenceCommand(
    val runIdText: String,
    val outputPath: Path?,
    val baselinePath: Path?,
    val format: EvidenceFormat,
    val check: Boolean,
    val forceOutput: Boolean,
)

private enum class TraceFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private enum class ReplayFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private enum class DiffFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private enum class EvidenceFormat(val id: String) {
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
private data class RunReplayReport(
    val schema: String,
    val version: String,
    val replayedAt: String,
    val source: String,
    val capsuleSchema: String,
    val run: RunCapsuleRun,
    val valid: Boolean,
    val deterministic: Boolean,
    val issues: List<String>,
    val checks: RunReplayChecks,
    val provenance: RunReplayProvenance,
    val metrics: ProcessTraceMetrics,
    val path: List<String>,
)

@Serializable
private data class RunReplayChecks(
    val capsuleContract: Boolean,
    val embeddedSnapshotHash: Boolean,
    val traceHash: Boolean,
    val rebuiltTraceMatchesEmbedded: Boolean,
    val savedSnapshotHashChecked: Boolean,
)

@Serializable
private data class RunReplayProvenance(
    val snapshotSha256: String,
    val embeddedSnapshotSha256: String?,
    val traceSha256: String,
    val rebuiltTraceSha256: String,
)

@Serializable
private data class RunDiffReport(
    val schema: String,
    val version: String,
    val comparedAt: String,
    val result: String,
    val valid: Boolean,
    val same: Boolean,
    val left: RunDiffSide,
    val right: RunDiffSide,
    val checks: RunDiffChecks,
    val metricsDelta: RunDiffMetricsDelta,
    val differences: List<RunDiffDifference>,
    val issues: List<String>,
)

@Serializable
private data class RunDiffSide(
    val source: String,
    val run: RunCapsuleRun,
    val deterministic: Boolean,
    val stableSha256: String,
    val issues: List<String>,
)

@Serializable
private data class RunDiffChecks(
    val leftCapsule: Boolean,
    val rightCapsule: Boolean,
    val leftReplay: Boolean,
    val rightReplay: Boolean,
)

@Serializable
private data class RunDiffMetricsDelta(
    val processCount: Int,
    val tokenTotal: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextBytes: Int,
    val syscallCount: Int,
    val eventCount: Int,
)

@Serializable
private data class RunDiffDifference(
    val field: String,
    val left: String,
    val right: String,
)

@Serializable
private data class RunEvidenceReport(
    val schema: String,
    val version: String,
    val generatedAt: String,
    val runId: String,
    val capsulePath: String,
    val valid: Boolean,
    val status: String,
    val capsule: EvidenceCapsuleStatus,
    val replay: EvidenceReplayStatus,
    val diff: EvidenceDiffStatus,
    val next: List<String>,
    val nextActions: List<NextAction>,
)

@Serializable
private data class EvidenceCapsuleStatus(
    val status: String,
    val valid: Boolean,
    val schema: String,
    val snapshotSha256: String,
    val traceSha256: String,
    val issues: List<String>,
)

@Serializable
private data class EvidenceReplayStatus(
    val status: String,
    val valid: Boolean,
    val deterministic: Boolean,
    val rebuiltTraceSha256: String,
    val issues: List<String>,
)

@Serializable
private data class EvidenceDiffStatus(
    val status: String,
    val valid: Boolean,
    val same: Boolean?,
    val baselinePath: String?,
    val baselineRunId: String?,
    val currentRunId: String?,
    val leftStableSha256: String?,
    val rightStableSha256: String?,
    val differences: Int,
    val changes: List<RunDiffDifference>,
    val issues: List<String>,
)

@Serializable
private data class StableRunSignature(
    val workflowName: String,
    val task: String,
    val success: Boolean,
    val finalOutputSha256: String,
    val metrics: StableRunMetrics,
    val path: List<String>,
    val processes: List<StableRunProcess>,
    val eventCounts: Map<String, Int>,
)

@Serializable
private data class StableRunMetrics(
    val processCount: Int,
    val tokenTotal: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextBytes: Int,
    val syscallCount: Int,
    val eventCount: Int,
)

@Serializable
private data class StableRunProcess(
    val agent: String,
    val state: String,
    val tokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextBytes: Int,
    val syscallCount: Int,
    val failure: String?,
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

private data class NextCommand(
    val configPath: Path,
    val format: NextFormat,
)

private enum class NextFormat(val id: String) {
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
    val next: List<String>,
    val nextActions: List<NextAction>,
)

private data class DoctorCommand(
    val configPath: Path,
    val format: DoctorFormat,
    val fix: Boolean,
    val dryRun: Boolean,
    val writeCi: Boolean,
    val force: Boolean,
    val templateId: String,
)

private enum class DoctorFormat(val id: String) {
    Text("text"),
    Json("json"),
}

private data class BugReportCommand(
    val configPath: Path,
    val outputPath: Path?,
    val force: Boolean,
    val format: BugReportFormat,
)

private enum class BugReportFormat(val id: String) {
    Markdown("markdown"),
    Json("json"),
}

@Serializable
private data class NextReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val status: String,
    val action: NextAction,
    val fixFirst: NextAction?,
    val signals: List<NextSignal>,
    val next: List<String>,
    val nextActions: List<NextAction>,
)

@Serializable
private data class NextSignal(
    val name: String,
    val status: String,
    val detail: String,
)

@Serializable
private data class DoctorReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val config: String,
    val summary: DoctorSummary,
    val checks: List<DoctorReportCheck>,
    val next: List<String>,
    val nextActions: List<NextAction>,
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
private data class DoctorFixReport(
    val schema: String,
    val version: String,
    val cwd: String,
    val status: String,
    val dryRun: Boolean,
    val requestedTemplate: String,
    val config: String,
    val before: DoctorReport,
    val plan: DoctorFixPlan,
    val setup: SetupReport?,
    val after: DoctorReport?,
    val errors: List<String>,
    val next: List<String>,
    val nextActions: List<NextAction>,
)

@Serializable
private data class DoctorFixPlan(
    val dryRun: Boolean,
    val writeCi: Boolean,
    val force: Boolean,
    val writes: List<DoctorFixPlannedWrite>,
    val commands: List<String>,
    val notes: List<String>,
)

@Serializable
private data class DoctorFixPlannedWrite(
    val id: String,
    val path: String?,
    val action: String,
    val reason: String,
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
    val fixFirst: NextAction?,
    val next: List<String>,
    val nextActions: List<NextAction>,
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
