package ai.kaios

import java.nio.file.Files
import java.time.Duration
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRuntimeTest {
    @Test
    fun `registered tool call succeeds when agent has permission`() {
        val agent = AgentSpec(
            id = AgentId("agent"),
            allowedTools = setOf("echo"),
            permissions = setOf(ToolPermission.ECHO),
        )
        val result = ToolRegistry(listOf(EchoTool())).execute(
            agent,
            ToolCall("echo", mapOf("message" to "hello")),
        )

        assertTrue(result.ok)
        assertEquals("hello", result.output)
    }

    @Test
    fun `tool call is denied without permission`() {
        val agent = AgentSpec(
            id = AgentId("agent"),
            allowedTools = setOf("echo"),
            permissions = emptySet(),
        )
        val result = ToolRegistry(listOf(EchoTool())).execute(
            agent,
            ToolCall("echo", mapOf("message" to "hello")),
        )

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("lacks permission"))
    }

    @Test
    fun `capability grants produce audit and cost records`() {
        val agent = AgentSpec(
            id = AgentId("agent"),
            capabilities = setOf(
                ToolCapabilityGrant(
                    tool = "echo",
                    permission = ToolPermission.ECHO,
                    cost = ToolCostProfile(estimatedMicros = 25),
                ),
            ),
        )
        val registry = ToolRegistry(listOf(EchoTool()))

        val result = registry.execute(
            agent,
            ToolCall("echo", mapOf("message" to "hello", "apiToken" to "secret")),
            ToolExecutionContext(runId = RunId("run-audit"), pid = ProcessId(7), agent = agent.id),
        )
        val record = registry.auditRecords(RunId("run-audit")).single()

        assertTrue(result.ok)
        assertEquals(25, result.estimatedCostMicros)
        assertEquals("sys-1", record.callId)
        assertEquals(7, record.pid?.value)
        assertEquals("<redacted>", record.redactedArguments.getValue("apiToken"))
        assertEquals(25, registry.estimatedCostMicros(RunId("run-audit")))
    }

    @Test
    fun `capability scope and max calls deny syscalls before tool execution`() {
        var called = 0
        val tool = object : Tool {
            override val name: String = "echo"
            override val description: String = "counting echo"
            override val permission: ToolPermission = ToolPermission.ECHO

            override fun call(call: ToolCall): ToolResult {
                called += 1
                return ToolResult.success(name, call.arguments["message"].orEmpty())
            }
        }
        val agent = AgentSpec(
            id = AgentId("agent"),
            capabilities = setOf(
                ToolCapabilityGrant(
                    tool = "echo",
                    permission = ToolPermission.ECHO,
                    scope = "allowed",
                    limits = ToolCapabilityLimits(maxCalls = 1),
                ),
            ),
        )
        val registry = ToolRegistry(listOf(tool))

        val allowed = registry.execute(agent, ToolCall("echo", mapOf("message" to "allowed")))
        val scopeDenied = registry.execute(agent, ToolCall("echo", mapOf("message" to "denied")))
        val limitDenied = registry.execute(agent, ToolCall("echo", mapOf("message" to "allowed")))

        assertTrue(allowed.ok)
        assertFalse(scopeDenied.ok)
        assertTrue(scopeDenied.denied)
        assertFalse(limitDenied.ok)
        assertTrue(limitDenied.denied)
        assertEquals(1, called)
        assertEquals(3, registry.auditRecords().size)
    }

    @Test
    fun `runtime syscall count tracks denied syscalls too`() {
        val runtime = AgentRuntime()
        val process = runtime.spawn(AgentSpec(AgentId("agent")), RunId("run-tools"))
        runtime.start(process.pid)
        runtime.recordSyscall(process.pid, ToolResult.failure("echo", "permission denied"))

        assertEquals(1, runtime.process(process.pid)?.syscallCount)
        assertEquals(RuntimeEventType.TOOL_CALLED, runtime.events(RunId("run-tools")).last().type)
    }

    @Test
    fun `scoped file tool writes reads lists and checks files inside root`() {
        val root = Files.createTempDirectory("kaios-file-tool")
        val tool = ScopedFileTool(root)

        val write = tool.call(
            ToolCall(
                "file",
                mapOf(
                    "op" to "write",
                    "path" to "notes/plan.txt",
                    "content" to "agent file syscall",
                ),
            ),
        )
        val read = tool.call(ToolCall("file", mapOf("op" to "read", "path" to "notes/plan.txt")))
        val exists = tool.call(ToolCall("file", mapOf("op" to "exists", "path" to "notes/plan.txt")))
        val list = tool.call(ToolCall("file", mapOf("op" to "list", "path" to "notes")))

        assertTrue(write.ok)
        assertTrue(root.resolve("notes/plan.txt").exists())
        assertEquals("agent file syscall", root.resolve("notes/plan.txt").readText())
        assertEquals("agent file syscall", read.output)
        assertEquals("true", exists.output)
        assertEquals("plan.txt", list.output)
    }

    @Test
    fun `scoped file tool rejects path traversal and absolute paths`() {
        val root = Files.createTempDirectory("kaios-file-tool-scope")
        val tool = ScopedFileTool(root)

        val traversal = tool.call(ToolCall("file", mapOf("op" to "read", "path" to "../secret.txt")))
        val absolute = tool.call(ToolCall("file", mapOf("op" to "read", "path" to root.resolve("file.txt").toString())))

        assertFalse(traversal.ok)
        assertTrue(traversal.error.orEmpty().contains("escapes scoped root"))
        assertFalse(absolute.ok)
        assertTrue(absolute.error.orEmpty().contains("escapes scoped root"))
    }

    @Test
    fun `scoped file tool reports missing files`() {
        val root = Files.createTempDirectory("kaios-file-tool-missing")
        val tool = ScopedFileTool(root)

        val result = tool.call(ToolCall("file", mapOf("op" to "read", "path" to "missing.txt")))

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("does not exist"))
    }

    @Test
    fun `registry denies file syscall without file permission`() {
        val agent = AgentSpec(
            id = AgentId("agent"),
            allowedTools = setOf("file"),
            permissions = emptySet(),
        )
        val result = ToolRegistry(listOf(ScopedFileTool(Files.createTempDirectory("kaios-deny")))).execute(
            agent,
            ToolCall("file", mapOf("op" to "exists", "path" to "x.txt")),
        )

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("lacks permission"))
    }

    @Test
    fun `http tool denies real network when allowlist is empty`() {
        val tool = HttpTool(transport = HttpSyscallTransport { error("transport should not be called") })

        val result = tool.call(ToolCall("http", mapOf("url" to "https://example.com")))

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("KAIOS_HTTP_ALLOWLIST"))
    }

    @Test
    fun `http tool performs allowlisted request through transport`() {
        var request: HttpSyscallRequest? = null
        val tool = HttpTool(
            allowlist = listOf("example.com"),
            transport = HttpSyscallTransport { syscall ->
                request = syscall
                HttpSyscallResponse(200, "hello from http")
            },
        )

        val result = tool.call(ToolCall("http", mapOf("method" to "GET", "url" to "https://example.com/docs")))

        assertTrue(result.ok)
        assertEquals("GET", request?.method)
        assertEquals("https://example.com/docs", request?.uri.toString())
        assertTrue(result.output.contains("HTTP 200"))
        assertTrue(result.output.contains("hello from http"))
    }

    @Test
    fun `http tool enforces host and path allowlist`() {
        val tool = HttpTool(
            allowlist = listOf("https://api.example.com/v1", "*.docs.example.com"),
            transport = HttpSyscallTransport { HttpSyscallResponse(200, "ok") },
        )

        val allowedPath = tool.call(ToolCall("http", mapOf("url" to "https://api.example.com/v1/search")))
        val deniedPath = tool.call(ToolCall("http", mapOf("url" to "https://api.example.com/v2/search")))
        val allowedWildcard = tool.call(ToolCall("http", mapOf("url" to "https://guide.docs.example.com/index.html")))
        val deniedHost = tool.call(ToolCall("http", mapOf("url" to "https://evil.example.com/index.html")))

        assertTrue(allowedPath.ok)
        assertFalse(deniedPath.ok)
        assertTrue(allowedWildcard.ok)
        assertFalse(deniedHost.ok)
    }

    @Test
    fun `http tool does not allow malformed allowlist rules`() {
        val tool = HttpTool(
            allowlist = listOf("https://["),
            transport = HttpSyscallTransport { error("transport should not be called") },
        )

        val result = tool.call(ToolCall("http", mapOf("url" to "https://example.com")))

        assertFalse(result.ok)
        assertTrue(result.error.orEmpty().contains("Not in KAIOS_HTTP_ALLOWLIST"))
    }

    @Test
    fun `http tool truncates large responses`() {
        val tool = HttpTool(
            allowlist = listOf("example.com"),
            transport = HttpSyscallTransport { HttpSyscallResponse(200, "abcdef") },
            timeout = Duration.ofSeconds(1),
            maxResponseChars = 3,
        )

        val result = tool.call(ToolCall("http", mapOf("url" to "https://example.com")))

        assertTrue(result.ok)
        assertTrue(result.output.contains("abc"))
        assertTrue(result.output.contains("truncated at 3 chars"))
        assertTrue(!result.output.contains("def"))
    }

    @Test
    fun `agent DSL maps http tool to network permission`() {
        val spec = agent("researcher") {
            tool("http")
        }

        assertTrue("http" in spec.allowedTools)
        assertTrue(ToolPermission.NETWORK in spec.permissions)
    }
}
