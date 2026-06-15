package ai.kaios

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class StoredRunSnapshot(
    val runId: String,
    val workflowName: String,
    val task: String,
    val success: Boolean,
    val finalOutput: String,
    val processes: List<StoredProcess>,
    val events: List<StoredRuntimeEvent>,
    val scheduler: StoredScheduler = StoredScheduler(),
    val syscalls: List<StoredToolExecutionRecord> = emptyList(),
)

@Serializable
data class StoredProcess(
    val pid: Long,
    val agent: String,
    val state: String,
    val tokens: Int,
    val inputTokens: Int,
    val outputTokens: Int,
    val contextSize: Int,
    val syscallCount: Int,
    val durationMillis: Long,
    val failure: String? = null,
    val attempt: Int = 1,
    val parentPid: Long? = null,
    val recoveryOfPid: Long? = null,
    val failureKind: String? = null,
    val memoryScopeId: String? = null,
    val toolTimeMillis: Long = 0,
    val estimatedCostMicros: Long = 0,
    val deniedSyscallCount: Int = 0,
    val workerId: String? = null,
)

@Serializable
data class StoredRuntimeEvent(
    val timestamp: String,
    val pid: Long,
    val agent: String,
    val type: String,
    val message: String,
)

@Serializable
data class StoredScheduler(
    val executorBackend: String = "in-process",
    val priorityEnabled: Boolean = false,
    val triggerCount: Int = 0,
    val recoveryEnabled: Boolean = false,
)

@Serializable
data class StoredToolExecutionRecord(
    val callId: String,
    val runId: String? = null,
    val pid: Long? = null,
    val agent: String,
    val tool: String,
    val permission: String? = null,
    val allowed: Boolean,
    val denied: Boolean,
    val durationMillis: Long,
    val estimatedCostMicros: Long,
    val redactedArguments: Map<String, String>,
    val error: String? = null,
)

class FileRunSnapshotStore(
    private val root: Path = Paths.get(".kaios", "runs"),
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {
    fun save(task: String, result: WorkflowResult): Path {
        Files.createDirectories(root)
        val snapshot = result.toStoredSnapshot(task)
        val path = root.resolve("${result.runId.value}.json")
        path.writeText(json.encodeToString(snapshot))
        return path
    }

    fun load(runId: RunId): StoredRunSnapshot {
        val path = root.resolve("${runId.value}.json")
        require(path.exists()) { "Run snapshot '${runId.value}' was not found under $root." }
        return json.decodeFromString(path.readText())
    }

    fun list(): List<StoredRunSnapshot> {
        if (!root.exists()) return emptyList()

        return Files.list(root).use { paths ->
            paths
                .toList()
                .asSequence()
                .filter { path -> path.isRegularFile() && path.extension == "json" }
                .mapNotNull { path ->
                    runCatching { SnapshotFile(path, json.decodeFromString<StoredRunSnapshot>(path.readText())) }
                        .getOrNull()
                }
                .sortedWith(
                    compareByDescending<SnapshotFile> { snapshotFile -> snapshotFile.modifiedMillis() }
                        .thenByDescending { snapshotFile -> snapshotFile.snapshot.runId },
                )
                .map { snapshotFile -> snapshotFile.snapshot }
                .toList()
        }
    }

    fun pathFor(runId: RunId): Path = root.resolve("${runId.value}.json")

    private fun WorkflowResult.toStoredSnapshot(task: String): StoredRunSnapshot =
        StoredRunSnapshot(
            runId = runId.value,
            workflowName = workflowName,
            task = task,
            success = success,
            finalOutput = finalOutput,
            processes = processes.map { process ->
                StoredProcess(
                    pid = process.pid.value,
                    agent = process.agent.value,
                    state = process.state.name,
                    tokens = process.tokenUsage.total,
                    inputTokens = process.tokenUsage.input,
                    outputTokens = process.tokenUsage.output,
                    contextSize = process.contextSize,
                    syscallCount = process.syscallCount,
                    durationMillis = process.durationMillis,
                    failure = process.failure,
                    attempt = process.attempt,
                    parentPid = process.parentPid?.value,
                    recoveryOfPid = process.recoveryOfPid?.value,
                    failureKind = process.failureKind?.name,
                    memoryScopeId = process.memoryScopeId,
                    toolTimeMillis = process.toolTimeMillis,
                    estimatedCostMicros = process.estimatedCostMicros,
                    deniedSyscallCount = process.deniedSyscallCount,
                    workerId = process.workerId,
                )
            },
            events = events.map { event ->
                StoredRuntimeEvent(
                    timestamp = event.timestamp.toString(),
                    pid = event.pid.value,
                    agent = event.agent.value,
                    type = event.type.name,
                    message = event.message,
                )
            },
            scheduler = StoredScheduler(
                executorBackend = scheduler.executorBackend,
                priorityEnabled = scheduler.priorityEnabled,
                triggerCount = scheduler.triggerCount,
                recoveryEnabled = scheduler.recoveryEnabled,
            ),
            syscalls = syscalls.map { record ->
                StoredToolExecutionRecord(
                    callId = record.callId,
                    runId = record.runId?.value,
                    pid = record.pid?.value,
                    agent = record.agent.value,
                    tool = record.tool,
                    permission = record.permission?.name,
                    allowed = record.allowed,
                    denied = record.denied,
                    durationMillis = record.durationMillis,
                    estimatedCostMicros = record.estimatedCostMicros,
                    redactedArguments = record.redactedArguments,
                    error = record.error,
                )
            },
        )

    private data class SnapshotFile(
        val path: Path,
        val snapshot: StoredRunSnapshot,
    ) {
        fun modifiedMillis(): Long =
            runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrDefault(0L)
    }
}
