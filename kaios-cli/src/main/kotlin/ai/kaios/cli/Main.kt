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
import ai.kaios.StoredProcess
import ai.kaios.agent
import ai.kaios.builtInToolRegistry
import ai.kaios.workflow
import ai.kaios.Workflow
import ai.kaios.WorkflowScheduler
import java.io.PrintStream
import java.nio.file.Paths
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = KaiosCli().run(args, System.out, System.err)
    if (exitCode != 0) exitProcess(exitCode)
}

class KaiosCli(
    private val snapshotStore: FileRunSnapshotStore = FileRunSnapshotStore(defaultSnapshotRoot()),
) {
    fun run(args: Array<String>, out: PrintStream, err: PrintStream): Int {
        if (args.isEmpty()) {
            printUsage(err)
            return 1
        }

        return when (args.first()) {
            "run" -> runWorkflow(args.drop(1), out, err)
            "ps" -> printProcessTable(args.drop(1), out, err)
            "inspect" -> inspectRun(args.drop(1), out, err)
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
        val task = args.joinToString(" ").trim()
        if (task.isBlank()) {
            err.println("Usage: kaios run \"task\"")
            return 1
        }

        val memory = SessionMemoryStore()
        val modelProvider = runCatching { modelProviderFromEnv() }.getOrElse { error ->
            err.println(error.message)
            return 1
        }
        val runtime = AgentRuntime()
        val scheduler = WorkflowScheduler(
            runtime = runtime,
            modelProvider = modelProvider,
            tools = builtInToolRegistry(),
            memory = memory,
        )

        val result = scheduler.run(defaultWorkflow(memory), task)
        val path = snapshotStore.save(task, result)

        out.println("run_id: ${result.runId.value}")
        out.println("success: ${result.success}")
        out.println("snapshot: $path")
        out.println()
        out.println(result.finalOutput)
        out.println()
        out.println("next:")
        out.println("  kaios ps ${result.runId.value}")
        out.println("  kaios inspect ${result.runId.value}")
        return if (result.success) 0 else 2
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
              kaios run "task"
              kaios ps <run-id>
              kaios inspect <run-id>
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
}

fun modelProviderFromEnv(env: (String) -> String? = System::getenv): ModelProvider =
    when (val provider = env("KAIOS_MODEL_PROVIDER")?.lowercase()?.trim().orEmpty().ifBlank { "mock" }) {
        "mock" -> MockModelProvider()
        "openai", "openai-compatible" -> OpenAiCompatibleModelProvider(OpenAiCompatibleConfig.fromEnv(env))
        "ollama" -> OllamaModelProvider(OllamaConfig.fromEnv(env))
        else -> error("Unsupported KAIOS_MODEL_PROVIDER '$provider'. Use mock, openai, or ollama.")
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
