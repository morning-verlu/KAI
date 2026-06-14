package ai.kaios.cli

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

internal data class ContextBundle(
    val sources: List<ContextSource>,
    val truncated: Boolean,
    val maxChars: Int,
) {
    val totalChars: Int
        get() = sources.sumOf { it.content.length }

    fun inputFor(task: String): String {
        if (sources.isEmpty()) return task

        return buildString {
            append(task.trim())
            appendLine()
            appendLine()
            appendLine("[KAIOS_CONTEXT]")
            sources.forEach { source ->
                appendLine("### ${source.path}")
                appendLine("```")
                appendLine(source.content.trimEnd())
                appendLine("```")
            }
            if (truncated) {
                appendLine("Context was truncated at $maxChars characters.")
            }
            appendLine("[/KAIOS_CONTEXT]")
        }
    }

    fun taskSummary(task: String): String {
        if (sources.isEmpty()) return task

        return buildString {
            appendLine(task)
            appendLine()
            appendLine("Context:")
            sources.forEach { source ->
                val suffix = if (source.truncated) ", truncated from ${source.originalChars}" else ""
                appendLine("- ${source.path} (${source.content.length} chars$suffix)")
            }
            if (truncated) {
                appendLine("- total context truncated at $maxChars chars")
            }
        }.trimEnd()
    }

    companion object {
        val Empty: ContextBundle = ContextBundle(emptyList(), truncated = false, maxChars = 0)
    }
}

internal data class ContextSource(
    val path: String,
    val content: String,
    val originalChars: Int,
    val truncated: Boolean,
)

internal class ContextLoader(
    private val workingDir: Path,
    private val maxChars: Int = 80_000,
    private val maxFileChars: Int = 20_000,
    private val maxFiles: Int = 40,
) {
    fun load(paths: List<Path>): ContextBundle {
        if (paths.isEmpty()) return ContextBundle.Empty

        val files = paths
            .flatMap { expand(it) }
            .distinct()
            .sortedBy { displayPath(it) }

        require(files.isNotEmpty()) { "No context files found." }

        val sources = mutableListOf<ContextSource>()
        var remaining = maxChars
        var truncated = false

        files.take(maxFiles).forEach { file ->
            if (remaining <= 0) {
                truncated = true
                return@forEach
            }

            val text = readTextFile(file) ?: return@forEach
            val limit = minOf(maxFileChars, remaining, text.length)
            val content = text.take(limit)
            sources += ContextSource(
                path = displayPath(file),
                content = content,
                originalChars = text.length,
                truncated = limit < text.length,
            )
            remaining -= content.length
            if (limit < text.length) truncated = true
        }

        if (files.size > maxFiles) truncated = true
        require(sources.isNotEmpty()) { "No readable text context files found." }

        return ContextBundle(sources, truncated, maxChars)
    }

    private fun expand(path: Path): List<Path> {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.exists()) { "Context path '$path' was not found." }
        require(normalized.startsWith(workingDir)) { "Context path '$path' must stay inside $workingDir." }

        if (shouldSkip(normalized)) return emptyList()
        if (normalized.isRegularFile()) return listOf(normalized)
        if (!normalized.isDirectory()) return emptyList()

        return Files.walk(normalized).use { stream ->
            stream
                .toList()
                .filter { candidate -> candidate.isRegularFile() && !shouldSkip(candidate) && hasTextExtension(candidate) }
        }
    }

    private fun readTextFile(path: Path): String? {
        if (!hasTextExtension(path)) return null
        if (Files.size(path) > 1_000_000L) return null

        val text = runCatching { path.readText() }.getOrNull() ?: return null
        if ('\u0000' in text) return null
        return text
    }

    private fun hasTextExtension(path: Path): Boolean {
        val name = path.name
        if (name in textFileNames) return true

        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        if (extension.isBlank()) return false
        return extension.lowercase() in textExtensions
    }

    private fun shouldSkip(path: Path): Boolean =
        path.iterator().asSequence().any { segment -> segment.toString() in skippedSegments }

    private fun displayPath(path: Path): String =
        if (path.startsWith(workingDir)) workingDir.relativize(path).toString() else path.toString()

    private companion object {
        val skippedSegments = setOf(".git", ".gradle", ".idea", ".kaios", "build", "node_modules", "out", "target")
        val textFileNames = setOf(".gitignore", "Dockerfile", "LICENSE", "README", "Makefile")
        val textExtensions = setOf(
            "cfg",
            "css",
            "gradle",
            "html",
            "java",
            "js",
            "json",
            "kt",
            "kts",
            "md",
            "properties",
            "py",
            "rb",
            "sh",
            "toml",
            "ts",
            "txt",
            "xml",
            "yaml",
            "yml",
        )
    }
}
