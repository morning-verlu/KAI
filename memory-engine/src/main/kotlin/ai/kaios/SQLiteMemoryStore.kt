package ai.kaios

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

class SQLiteMemoryStore(
    private val databasePath: Path = Paths.get(".kaios", "kaios.db"),
) : MemoryStore {
    private val lock = Any()
    private val jdbcUrl: String

    init {
        Class.forName("org.sqlite.JDBC")
        databasePath.parent?.let { Files.createDirectories(it) }
        jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath().normalize()}"
        initializeSchema()
    }

    override fun append(entry: MemoryEntry) {
        synchronized(lock) {
            connection().use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO memory_entries(run_id, agent_id, role, content, timestamp)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, entry.runId.value)
                    statement.setString(2, entry.agent.value)
                    statement.setString(3, entry.role)
                    statement.setString(4, entry.content)
                    statement.setString(5, entry.timestamp.toString())
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun read(runId: RunId, agent: AgentId?): List<MemoryEntry> = synchronized(lock) {
        connection().use { connection ->
            val sql = if (agent == null) {
                """
                SELECT run_id, agent_id, role, content, timestamp
                FROM memory_entries
                WHERE run_id = ?
                ORDER BY timestamp ASC, id ASC
                """.trimIndent()
            } else {
                """
                SELECT run_id, agent_id, role, content, timestamp
                FROM memory_entries
                WHERE run_id = ? AND agent_id = ?
                ORDER BY timestamp ASC, id ASC
                """.trimIndent()
            }

            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, runId.value)
                if (agent != null) {
                    statement.setString(2, agent.value)
                }

                statement.executeQuery().use { results ->
                    val entries = mutableListOf<MemoryEntry>()
                    while (results.next()) {
                        entries += MemoryEntry(
                            runId = RunId(results.getString("run_id")),
                            agent = AgentId(results.getString("agent_id")),
                            role = results.getString("role"),
                            content = results.getString("content"),
                            timestamp = Instant.parse(results.getString("timestamp")),
                        )
                    }
                    entries
                }
            }
        }
    }

    override fun clear(runId: RunId) {
        synchronized(lock) {
            connection().use { connection ->
                connection.prepareStatement("DELETE FROM memory_entries WHERE run_id = ?").use { statement ->
                    statement.setString(1, runId.value)
                    statement.executeUpdate()
                }
            }
        }
    }

    private fun initializeSchema() {
        synchronized(lock) {
            connection().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS memory_entries (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            run_id TEXT NOT NULL,
                            agent_id TEXT NOT NULL,
                            role TEXT NOT NULL,
                            content TEXT NOT NULL,
                            timestamp TEXT NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.executeUpdate(
                        """
                        CREATE INDEX IF NOT EXISTS idx_memory_entries_run_agent_time
                        ON memory_entries(run_id, agent_id, timestamp, id)
                        """.trimIndent(),
                    )
                }
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(jdbcUrl)
}
