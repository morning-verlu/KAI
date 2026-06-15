package ai.kaios.cli

import ai.kaios.StoredRunSnapshot

class ArtifactExporter {
    fun render(snapshot: StoredRunSnapshot): String {
        val taskSections = splitTask(snapshot.task)
        return buildString {
            appendLine("# KAI OS Run ${snapshot.runId}")
            appendLine()
            appendLine("## Operational Summary")
            appendLine()
            renderOperationalSummary(snapshot, taskSections.hasInputs()).forEach { line ->
                appendLine("- $line")
            }
            appendLine()
            appendLine("## Task")
            appendLine()
            appendLine(taskSections.task.ifBlank { "(empty)" })
            if (taskSections.inputs.isNotBlank()) {
                appendLine()
                appendLine("## Inputs")
                appendLine()
                appendLine(taskSections.inputs)
            }
            appendLine()
            appendLine("## Final Output")
            appendLine()
            appendLine(snapshot.finalOutput.ifBlank { "(empty)" })
            appendLine()
            appendLine("## Process Table")
            appendLine()
            appendLine("| PID | Agent | State | Tokens | Memory | Syscalls | Tool ms | Cost | Duration |")
            appendLine("| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |")
            snapshot.processes.forEach { process ->
                appendLine(
                    "| ${process.pid} | ${escapeCell(process.agent)} | ${process.state} | " +
                        "${process.tokens} | ${process.contextSize}b | ${process.syscallCount} | " +
                        "${process.toolTimeMillis} | ${formatCost(process.estimatedCostMicros)} | ${process.durationMillis}ms |",
                )
            }
            appendLine()
            appendLine("## Lifecycle Events")
            appendLine()
            if (snapshot.events.isEmpty()) {
                appendLine("(no events)")
            } else {
                snapshot.events.forEach { event ->
                    appendLine(
                        "- `${event.timestamp}` pid=${event.pid} agent=${escapeInline(event.agent)} " +
                            "${event.type}: ${event.message}",
                    )
                }
            }
            appendLine()
        }
    }

    private fun renderOperationalSummary(snapshot: StoredRunSnapshot, hasInputs: Boolean): List<String> {
        val processCount = snapshot.processes.size
        val stateSummary = snapshot.processes
            .groupingBy { process -> process.state }
            .eachCount()
            .entries
            .sortedBy { (state, _) -> state }
            .joinToString(", ") { (state, count) -> "$state=$count" }
            .ifBlank { "none" }
        val tokens = snapshot.processes.sumOf { process -> process.tokens }
        val inputTokens = snapshot.processes.sumOf { process -> process.inputTokens }
        val outputTokens = snapshot.processes.sumOf { process -> process.outputTokens }
        val contextBytes = snapshot.processes.sumOf { process -> process.contextSize }
        val syscalls = snapshot.processes.sumOf { process -> process.syscallCount }
        val processMillis = snapshot.processes.sumOf { process -> process.durationMillis }
        val toolMillis = snapshot.processes.sumOf { process -> process.toolTimeMillis }
        val cost = snapshot.processes.sumOf { process -> process.estimatedCostMicros }
        val verdict = if (snapshot.success) "Succeeded" else "Failed"
        val inputSummary = if (hasInputs) {
            "Task plus bounded workspace input summaries were attached."
        } else {
            "Task only; no workspace index or context summary was attached."
        }

        return listOf(
            "Verdict: $verdict for `${snapshot.workflowName}` with ${countLabel(processCount, "agent process", "agent processes")}: $stateSummary.",
            "Runtime cost: `$tokens` ${unit(tokens, "token")} (`$inputTokens` input / `$outputTokens` output), `${contextBytes}b` context, `$syscalls` ${unit(syscalls, "syscall")}, `${toolMillis}ms` tool time, `${formatCost(cost)}` estimated money, `${processMillis}ms` process time.",
            "Inputs: $inputSummary",
            "Inspectability: process table and lifecycle events are embedded below; run `kaios trace ${snapshot.runId} --check` to validate the saved trace contract.",
            "Next: `kaios ps ${snapshot.runId}`, `kaios inspect ${snapshot.runId}`, or `kaios evidence ${snapshot.runId} --out artifacts/run.capsule.json --force`.",
        )
    }

    private fun splitTask(task: String): TaskSections {
        val trimmed = task.trim()
        val firstSection = listOf(
            trimmed.indexOf("\n\nWorkspace Index:"),
            trimmed.indexOf("\n\nContext:"),
        ).filter { index -> index >= 0 }.minOrNull()

        return if (firstSection == null) {
            TaskSections(task = trimmed, inputs = "")
        } else {
            TaskSections(
                task = trimmed.substring(0, firstSection).trim(),
                inputs = trimmed.substring(firstSection).trim(),
            )
        }
    }

    private data class TaskSections(
        val task: String,
        val inputs: String,
    ) {
        fun hasInputs(): Boolean = inputs.isNotBlank()
    }

    private fun escapeCell(value: String): String =
        value.replace("|", "\\|")

    private fun escapeInline(value: String): String =
        value.replace("`", "'")

    private fun countLabel(count: Int, singular: String, plural: String = "${singular}s"): String =
        "`$count` ${unit(count, singular, plural)}"

    private fun unit(count: Int, singular: String, plural: String = "${singular}s"): String =
        if (count == 1) singular else plural

    private fun formatCost(micros: Long): String =
        if (micros == 0L) "0" else "${micros}um"
}
