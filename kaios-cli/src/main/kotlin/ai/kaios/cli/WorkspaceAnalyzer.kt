package ai.kaios.cli

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.util.concurrent.TimeUnit

internal class WorkspaceAnalyzer {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun analyze(index: WorkspaceIndex): WorkspaceAnalysis {
        val changeSummary = detectWorkspaceGitChanges(index.root)
        return WorkspaceAnalysis(
            schemaVersion = 1,
            summary = WorkspaceAnalysisSummary(
                root = index.root.toString(),
                files = index.files.size,
                lines = index.totalLines,
                bytes = index.totalBytes,
                truncated = index.truncated,
                maxFiles = index.maxFiles,
                ignorePatternCount = index.ignorePatternCount,
            ),
            stackSignals = stackSignals(index),
            languages = index.languageStats.map { stat ->
                WorkspaceAnalysisLanguage(stat.language, stat.files, stat.lines, stat.bytes)
            },
            directories = index.directoryStats.take(16).map { stat ->
                WorkspaceAnalysisDirectory(stat.directory, stat.files, stat.bytes)
            },
            notableFiles = index.notableFiles.ifEmpty { index.largestFiles.take(10) }.map { file ->
                WorkspaceAnalysisFile(file.path, file.language, file.lines, file.bytes)
            },
            hotspots = index.largestFiles.take(10).map { file ->
                WorkspaceAnalysisFile(file.path, file.language, file.lines, file.bytes)
            },
            qualitySignals = qualitySignals(index),
            changeSummary = changeSummary,
            actionPlan = actionPlan(index, changeSummary),
            suggestedCommands = suggestedCommands(index),
        )
    }

    fun render(index: WorkspaceIndex): String = renderMarkdown(analyze(index))

    fun renderJson(index: WorkspaceIndex): String =
        json.encodeToString(analyze(index))

    private fun renderMarkdown(analysis: WorkspaceAnalysis): String = buildString {
        appendLine("# KAI OS Workspace Analysis")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine("- Root: `${analysis.summary.root}`")
        appendLine("- Files indexed: `${analysis.summary.files}`")
        appendLine("- Lines indexed: `${analysis.summary.lines}`")
        appendLine("- Bytes indexed: `${analysis.summary.bytes}`")
        appendLine("- Truncated: `${analysis.summary.truncated}`")
        if (analysis.summary.ignorePatternCount > 0) {
            appendLine("- Ignore rules: `.kaiosignore` with `${analysis.summary.ignorePatternCount}` pattern(s)")
        }
        appendLine()

        appendLine("## Stack Signals")
        appendLine()
        analysis.stackSignals.forEach { appendLine("- $it") }
        appendLine()

        appendLine("## Language Map")
        appendLine()
        appendLine("| Language | Files | Lines | Bytes |")
        appendLine("| --- | ---: | ---: | ---: |")
        analysis.languages.forEach { stat ->
            appendLine("| ${stat.language} | ${stat.files} | ${stat.lines} | ${stat.bytes} |")
        }
        appendLine()

        appendLine("## Architecture Map")
        appendLine()
        appendLine("| Directory | Files | Bytes |")
        appendLine("| --- | ---: | ---: |")
        analysis.directories.forEach { stat ->
            appendLine("| ${escapeCell(stat.directory)} | ${stat.files} | ${stat.bytes} |")
        }
        appendLine()

        appendLine("## Notable Files")
        appendLine()
        analysis.notableFiles.forEach { file ->
            appendLine("- `${file.path}` - ${file.language}, ${file.lines} lines, ${file.bytes} bytes")
        }
        appendLine()

        appendLine("## Hotspots")
        appendLine()
        analysis.hotspots.forEach { file ->
            appendLine("- `${file.path}` - ${file.language}, ${file.lines} lines, ${file.bytes} bytes")
        }
        appendLine()

        appendLine("## Test And Quality Signals")
        appendLine()
        analysis.qualitySignals.forEach { appendLine("- $it") }
        appendLine()

        if (analysis.changeSummary.available) {
            appendLine("## Change Signals")
            appendLine()
            if (analysis.changeSummary.dirty) {
                appendLine("- Git working tree has `${analysis.changeSummary.changedFiles}` changed file(s).")
                if (analysis.changeSummary.untrackedFiles > 0) {
                    appendLine("- Untracked files: `${analysis.changeSummary.untrackedFiles}`.")
                }
                analysis.changeSummary.files.take(12).forEach { file ->
                    appendLine("- `${file.status}` `${file.path}`")
                }
            } else {
                appendLine("- Git working tree is clean.")
            }
            appendLine()
        }

        appendLine("## Recommended Action Plan")
        appendLine()
        appendLine("| Priority | Action | Command | Why |")
        appendLine("| --- | --- | --- | --- |")
        analysis.actionPlan.forEach { action ->
            appendLine("| ${action.priority} | ${escapeCell(action.action)} | `${escapeCell(action.command)}` | ${escapeCell(action.reason)} |")
        }
        appendLine()

        appendLine("## Suggested KAI OS Commands")
        appendLine()
        analysis.suggestedCommands.forEach { command ->
            appendLine("```bash")
            appendLine(command)
            appendLine("```")
            appendLine()
        }
    }.trimEnd()

    private fun stackSignals(index: WorkspaceIndex): List<String> {
        val files = index.files.map { it.path }.toSet()
        val languages = index.languageStats.associate { it.language to it.files }
        val signals = mutableListOf<String>()

        when {
            "settings.gradle.kts" in files || "build.gradle.kts" in files -> signals += "Gradle Kotlin DSL project detected."
            "settings.gradle" in files || "build.gradle" in files -> signals += "Gradle project detected."
            "pom.xml" in files -> signals += "Maven project detected."
            "package.json" in files -> signals += "Node.js package detected."
            "pyproject.toml" in files -> signals += "Python project metadata detected."
            else -> signals += "No dominant build manifest detected in indexed text files."
        }

        if ((languages["Kotlin"] ?: 0) > 0) signals += "Kotlin is present and likely a primary implementation language."
        if ((languages["Java"] ?: 0) > 0) signals += "Java sources are present."
        if ((languages["TypeScript"] ?: 0) + (languages["JavaScript"] ?: 0) > 0) signals += "JavaScript/TypeScript sources are present."
        if ("README.md" in files || "README" in files) signals += "README documentation is present."
        if ("LICENSE" in files) signals += "License file is present."
        if ("SECURITY.md" in files) signals += "Security policy is present."
        if (files.any { it.startsWith(".github/workflows/") }) signals += "GitHub Actions workflow files are present."
        if (files.any { it.endsWith("kaios.json") }) signals += "KAI OS project workflow config is present."

        return signals
    }

    private fun qualitySignals(index: WorkspaceIndex): List<String> {
        val files = index.files.map { it.path }
        val testFiles = files.filter { "/src/test/" in it || it.startsWith("test/") || it.contains("/test/") }
        val docs = files.filter { it.startsWith("docs/") && it.endsWith(".md") }
        val sourceFiles = files.filter { "/src/main/" in it || it.startsWith("src/") }
        val signals = mutableListOf<String>()

        signals += if (testFiles.isEmpty()) {
            "No test files were found in the indexed text set."
        } else {
            "${testFiles.size} test file(s) found."
        }

        signals += if (docs.isEmpty()) {
            "No Markdown docs under `docs/` were found."
        } else {
            "${docs.size} documentation file(s) found under `docs/`."
        }

        signals += if (sourceFiles.isEmpty()) {
            "No source directory pattern was detected."
        } else {
            "${sourceFiles.size} source file(s) found under source-style directories."
        }

        val largest = index.largestFiles.firstOrNull()
        if (largest != null && largest.lines > 600) {
            signals += "Largest file `${largest.path}` has ${largest.lines} lines; consider splitting or documenting its internal sections."
        }

        if (index.truncated) {
            signals += "Index was truncated at ${index.maxFiles} files; raise `KAIOS_INDEX_MAX_FILES` for a fuller map."
        }

        return signals
    }

    private fun actionPlan(index: WorkspaceIndex, changeSummary: WorkspaceChangeSummary): List<WorkspaceAnalysisAction> {
        val files = index.files.map { it.path }
        val indexedFiles = files.toSet()
        val hasKaiosConfig = files.any { it == "kaios.json" }
        val hasGradle = files.any { it == "settings.gradle.kts" || it == "build.gradle.kts" || it == "settings.gradle" || it == "build.gradle" }
        val hasTests = files.any { "/src/test/" in it || it.startsWith("test/") || it.contains("/test/") }
        val readme = index.files.firstOrNull { it.path.equals("README.md", ignoreCase = true) || it.path == "README" }
        val largest = index.largestFiles.firstOrNull()

        val actions = mutableListOf<WorkspaceAnalysisAction>()
        val changedContextFiles = changeSummary.files
            .map { it.path }
            .filter { it in indexedFiles }
            .take(4)

        if (changedContextFiles.isNotEmpty()) {
            actions += WorkspaceAnalysisAction(
                id = "review-current-change",
                priority = "P0",
                action = "Review the current change set",
                command = buildString {
                    append("kaios run --index .")
                    changedContextFiles.forEach { path -> append(" --context ${shellArg(path)}") }
                    append(" --out artifacts/change-review.md --force \"review current code change\"")
                },
                reason = "Git has ${changeSummary.changedFiles} changed file(s); attach the changed files as bounded context before asking an agent to review them.",
            )
        }

        if (hasKaiosConfig) {
            actions += WorkspaceAnalysisAction(
                id = "verify-agent-runtime",
                priority = "P0",
                action = "Verify the agent runtime contract",
                command = "kaios gate --config kaios.json",
                reason = "A KAI OS workflow config exists, so the highest-value check is the deterministic readiness gate plus evidence capsule.",
            )
        } else {
            actions += WorkspaceAnalysisAction(
                id = "preview-onboarding",
                priority = "P0",
                action = "Preview KAI OS onboarding writes",
                command = "kaios quickstart --dry-run",
                reason = "No kaios.json was found; preview the generated workflow and evidence path before writing files.",
            )
        }

        actions += WorkspaceAnalysisAction(
            id = "create-project-artifact",
            priority = "P1",
            action = "Create a reviewable project artifact",
            command = if (readme == null) {
                "kaios run --index . --out artifacts/project.md --force \"summarize this project\""
            } else {
                "kaios run --index . --context ${shellArg(readme.path)} --out artifacts/project.md --force \"summarize this project\""
            },
            reason = "Turn the static workspace map into a saved run, process table, trace, and Markdown handoff artifact.",
        )

        if (hasGradle) {
            actions += WorkspaceAnalysisAction(
                id = "run-tests",
                priority = if (hasTests) "P1" else "P2",
                action = "Run the project test gate",
                command = "./gradlew test",
                reason = if (hasTests) {
                    "Gradle and test files were detected; run the native test gate before trusting agent output."
                } else {
                    "Gradle was detected; even without indexed tests, the native test task is the fastest health check."
                },
            )
        }

        if (largest != null && largest.lines > 600) {
            actions += WorkspaceAnalysisAction(
                id = "inspect-largest-hotspot",
                priority = "P2",
                action = "Inspect the largest hotspot",
                command = "kaios context ${shellArg(largest.path)}",
                reason = "`${largest.path}` has ${largest.lines} lines; preview bounded context before asking an agent to reason about it.",
            )
        }

        if (index.truncated) {
            actions += WorkspaceAnalysisAction(
                id = "expand-workspace-map",
                priority = "P2",
                action = "Expand the workspace map",
                command = "KAIOS_INDEX_MAX_FILES=${index.maxFiles * 2} kaios analyze . --out artifacts/analysis.md --force",
                reason = "The index hit its file limit, so the current report may miss important files.",
            )
        }

        return actions.distinctBy { it.id }
    }

    private fun suggestedCommands(index: WorkspaceIndex): List<String> {
        val commands = mutableListOf(
            "kaios index .",
            "kaios run --index . \"summarize the project shape\"",
        )

        val readme = index.files.firstOrNull { it.path.equals("README.md", ignoreCase = true) || it.path == "README" }
        if (readme != null) {
            commands += "kaios run --index . --context ${readme.path} --out artifacts/project.md \"summarize this project\""
        }

        if (index.files.any { it.path.startsWith("docs/") }) {
            commands += "kaios run --index . --context docs --out artifacts/architecture.md \"explain the architecture\""
        }

        if (index.files.any { it.path == "kaios.json" }) {
            commands += "kaios config show"
        } else {
            commands += "kaios init --template research"
        }

        return commands.distinct()
    }

    private fun escapeCell(value: String): String =
        value.replace("|", "\\|")

    private fun shellArg(value: String): String =
        if (value.all { it.isLetterOrDigit() || it in setOf('/', '.', '_', '-') }) {
            value
        } else {
            "'${value.replace("'", "'\"'\"'")}'"
        }
}

internal fun detectWorkspaceGitChanges(root: Path): WorkspaceChangeSummary {
    val process = runCatching {
        ProcessBuilder("git", "-C", root.toString(), "status", "--porcelain=v1", "--untracked-files=all")
            .redirectErrorStream(true)
            .start()
    }.getOrNull() ?: return WorkspaceChangeSummary.Unavailable

    val finished = process.waitFor(2, TimeUnit.SECONDS)
    if (!finished) {
        process.destroyForcibly()
        return WorkspaceChangeSummary.Unavailable
    }
    if (process.exitValue() != 0) return WorkspaceChangeSummary.Unavailable

    val files = process.inputStream
        .bufferedReader()
        .readLines()
        .mapNotNull(::parseGitStatusLine)

    return WorkspaceChangeSummary(
        available = true,
        dirty = files.isNotEmpty(),
        changedFiles = files.size,
        untrackedFiles = files.count { it.status == "??" },
        files = files.take(20),
    )
}

private fun parseGitStatusLine(line: String): WorkspaceChangedFile? {
    if (line.length < 4) return null
    val status = line.take(2).trim().ifBlank { line.take(2) }
    val rawPath = line.drop(3)
    val path = rawPath.substringAfter(" -> ", rawPath).trim().trim('"')
    if (path.isBlank()) return null
    return WorkspaceChangedFile(status = status, path = path)
}

@Serializable
internal data class WorkspaceAnalysis(
    val schemaVersion: Int,
    val summary: WorkspaceAnalysisSummary,
    val stackSignals: List<String>,
    val languages: List<WorkspaceAnalysisLanguage>,
    val directories: List<WorkspaceAnalysisDirectory>,
    val notableFiles: List<WorkspaceAnalysisFile>,
    val hotspots: List<WorkspaceAnalysisFile>,
    val qualitySignals: List<String>,
    val changeSummary: WorkspaceChangeSummary,
    val actionPlan: List<WorkspaceAnalysisAction>,
    val suggestedCommands: List<String>,
)

@Serializable
internal data class WorkspaceAnalysisSummary(
    val root: String,
    val files: Int,
    val lines: Int,
    val bytes: Long,
    val truncated: Boolean,
    val maxFiles: Int,
    val ignorePatternCount: Int,
)

@Serializable
internal data class WorkspaceAnalysisLanguage(
    val language: String,
    val files: Int,
    val lines: Int,
    val bytes: Long,
)

@Serializable
internal data class WorkspaceAnalysisDirectory(
    val directory: String,
    val files: Int,
    val bytes: Long,
)

@Serializable
internal data class WorkspaceAnalysisFile(
    val path: String,
    val language: String,
    val lines: Int,
    val bytes: Long,
)

@Serializable
internal data class WorkspaceChangeSummary(
    val available: Boolean,
    val dirty: Boolean,
    val changedFiles: Int,
    val untrackedFiles: Int,
    val files: List<WorkspaceChangedFile>,
) {
    companion object {
        val Unavailable = WorkspaceChangeSummary(
            available = false,
            dirty = false,
            changedFiles = 0,
            untrackedFiles = 0,
            files = emptyList(),
        )
    }
}

@Serializable
internal data class WorkspaceChangedFile(
    val status: String,
    val path: String,
)

@Serializable
internal data class WorkspaceAnalysisAction(
    val id: String,
    val priority: String,
    val action: String,
    val command: String,
    val reason: String,
)
