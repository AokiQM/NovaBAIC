package com.verlintas.baic2.mcp

import com.verlintas.baic2.core.model.McpServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before

class McpClientTest {

    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = McpClient(OkHttpClient(), json)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mcpServer() = McpServer(
        id = 1,
        name = "test",
        url = server.url("/mcp").toString(),
    )

    @Test
    fun initializesAndListsTools() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "sess-1")
                .setBody("""{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26"}}"""),
        )
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"weather","description":"w","inputSchema":{"type":"object"},"annotations":{"readOnlyHint":true}}]}}""",
                ),
        )

        val session = client.initialize(mcpServer()).getOrThrow()
        assertEquals("sess-1", session.sessionId)
        val tools = client.listTools(session).getOrThrow()
        assertEquals(1, tools.size)
        assertEquals("weather", tools[0].name)
        assertTrue(tools[0].readOnly)
    }

    @Test
    fun callsToolAndJoinsTextContent() = runTest {
        val session = McpSession(mcpServer(), "sess-1", "2025-03-26")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"18C"},{"type":"text","text":"sunny"}]}}""",
                ),
        )

        val result = client.callTool(session, "weather", """{"city":"SF"}""").getOrThrow()

        assertEquals("18C\nsunny", result)
        val recorded = server.takeRequest()
        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("tools/call", body.getValue("method").jsonPrimitive.content)
        assertEquals("sess-1", recorded.getHeader("Mcp-Session-Id"))
    }

    @Test
    fun parsesSseResponses() = runTest {
        val session = McpSession(mcpServer(), null, "2025-03-26")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"via-sse\"}]}}\n\n",
                ),
        )

        val result = client.callTool(session, "ping", "{}").getOrThrow()

        assertEquals("via-sse", result)
    }

    @Test
    fun surfacesJsonRpcErrors() = runTest {
        val session = McpSession(mcpServer(), null, "2025-03-26")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"no such tool"}}"""),
        )

        val failure = client.callTool(session, "nope", "{}").exceptionOrNull()

        assertTrue(failure != null && failure.message!!.contains("no such tool"))
    }

    @Test
    fun toolErrorsBecomeFailures() = runTest {
        val session = McpSession(mcpServer(), null, "2025-03-26")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"jsonrpc":"2.0","id":5,"result":{"isError":true,"content":[{"type":"text","text":"boom"}]}}""",
                ),
        )

        val failure = client.callTool(session, "x", "{}").exceptionOrNull()

        assertTrue(failure is McpToolError && failure.message == "boom")
    }
}
