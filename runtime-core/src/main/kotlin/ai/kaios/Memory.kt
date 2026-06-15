package ai.kaios

import java.time.Instant

data class MemoryEntry(
    val runId: RunId,
    val agent: AgentId,
    val role: String,
    val content: String,
    val timestamp: Instant,
    val scopeId: String? = null,
)

interface MemoryStore {
    fun append(entry: MemoryEntry)

    fun read(runId: RunId, agent: AgentId? = null, scopeId: String? = null): List<MemoryEntry>

    fun clear(runId: RunId)
}

object NoopMemoryStore : MemoryStore {
    override fun append(entry: MemoryEntry) = Unit

    override fun read(runId: RunId, agent: AgentId?, scopeId: String?): List<MemoryEntry> = emptyList()

    override fun clear(runId: RunId) = Unit
}
