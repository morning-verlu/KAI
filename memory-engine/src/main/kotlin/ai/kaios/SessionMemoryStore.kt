package ai.kaios

class SessionMemoryStore : MemoryStore {
    private val lock = Any()
    private val entries = mutableListOf<MemoryEntry>()

    override fun append(entry: MemoryEntry) {
        synchronized(lock) {
            entries += entry
        }
    }

    override fun read(runId: RunId, agent: AgentId?, scopeId: String?): List<MemoryEntry> = synchronized(lock) {
        entries
            .filter { it.runId == runId && (agent == null || it.agent == agent) && (scopeId == null || it.scopeId == scopeId) }
            .sortedBy { it.timestamp }
    }

    override fun clear(runId: RunId) {
        synchronized(lock) {
            entries.removeAll { it.runId == runId }
        }
    }
}
