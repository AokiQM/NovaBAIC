package com.verlintas.baic2.core.network.provider.gemini

import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class GeminiProviderTest {

    private lateinit var server: MockWebServer
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val provider = GeminiProvider(OkHttpClient(), json)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config() = ProviderConfig(
        provider = ProviderId.GEMINI,
        baseUrl = server.url("").toString().trimEnd('/'),
        apiKey = "AIza-test",
        model = "gemini-test",
    )

    private fun request(
        messages: List<ChatMessage> = listOf(ChatMessage(role = ChatRole.USER, content = "hi")),
        tools: List<ToolSpec> = emptyList(),
    ) = ChatRequest(
        config = config(),
        systemPrompt = "be nice",
        messages = messages,
        tools = tools,
    )

    private fun enqueueSse(vararg payloads: String) {
        val body = buildString {
            payloads.forEach { append("data: ").append(it).append("\n\n") }
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
    }

    @Test
    fun streamsTextDeltasAndUsage() = runTest {
        enqueueSse(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"Hel"}]}}]}""",
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"lo"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}""",
        )

        val events = provider.stream(request()).toList()

        assertEquals(
            listOf(
                StreamEvent.TextDelta("Hel"),
                StreamEvent.TextDelta("lo"),
                StreamEvent.Usage(7, 3),
                StreamEvent.Done,
            ),
            events,
        )
    }

    @Test
    fun mapsThoughtPartsToThinking() = runTest {
        enqueueSse(
            """{"candidates":[{"content":{"parts":[{"text":"thinking","thought":true},{"text":"answer"}]}}]}""",
        )

        val events = provider.stream(request()).toList()

        assertTrue(events.contains(StreamEvent.ThinkingDelta("thinking")))
        assertTrue(events.contains(StreamEvent.TextDelta("answer")))
    }

    @Test
    fun mapsFunctionCallsToToolCalls() = runTest {
        enqueueSse(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"get_weather","args":{"city":"SF"}}}]},"finishReason":"STOP"}]}""",
        )

        val events = provider.stream(request()).toList()
        val call = events.filterIsInstance<StreamEvent.ToolCallsDone>().single().calls.single()

        assertEquals("get_weather", call.name)
        assertTrue(call.id.startsWith("gcall_get_weather"))
        assertTrue(call.argumentsJson.contains("SF"))
    }

    @Test
    fun buildsRequestWithSystemInstructionFunctionResponseAndKey() = runTest {
        enqueueSse("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
        val history = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "remember: be brief"),
            ChatMessage(role = ChatRole.USER, content = "weather?"),
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    ToolCall(id = "gcall_1", name = "get_weather", argumentsJson = """{"city":"SF"}"""),
                ),
            ),
            ChatMessage(
                role = ChatRole.TOOL,
                content = "18C",
                toolCallId = "gcall_1",
                toolName = "get_weather",
            ),
        )

        provider.stream(
            request(
                messages = history,
                tools = listOf(ToolSpec(name = "get_weather", description = "weather")),
            ),
        ).toList()

        val recorded = server.takeRequest()
        assertEquals(
            "/v1beta/models/gemini-test:streamGenerateContent?alt=sse&key=AIza-test",
            recorded.path,
        )

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertTrue(body.getValue("systemInstruction").toString().contains("be nice"))
        assertTrue(body.getValue("systemInstruction").toString().contains("be brief"))
        val contents = body.getValue("contents").toString()
        assertTrue(contents.contains("functionCall"))
        assertTrue(contents.contains("functionResponse"))
        assertTrue(body.getValue("tools").toString().contains("get_weather"))
    }

    @Test
    fun mapsApiKeyError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"code":400,"message":"API key not valid","status":"INVALID_ARGUMENT"}}""",
                ),
        )

        val events = provider.stream(request()).toList()
        val failure = events.filterIsInstance<StreamEvent.Failed>().single()

        assertEquals(ProviderError.Kind.INVALID_REQUEST, failure.error.kind)
        assertEquals("API key not valid", failure.error.message)
    }

    @Test
    fun mapsImageAttachmentToInlineData() = runTest {
        enqueueSse("""{"candidates":[{"content":{"parts":[{"text":"ok"}]}}]}""")
        val history = listOf(
            ChatMessage(
                role = ChatRole.USER,
                content = "what is this?",
                attachments = listOf(
                    com.verlintas.baic2.core.model.Attachment(
                        id = "a1",
                        kind = com.verlintas.baic2.core.model.AttachmentKind.IMAGE,
                        mimeType = "image/jpeg",
                        base64 = "QUJD",
                    ),
                ),
            ),
        )

        provider.stream(request(messages = history)).toList()

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val contents = body.getValue("contents").toString()
        assertTrue(contents.contains("\"inlineData\""))
        assertTrue(contents.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(contents.contains("\"data\":\"QUJD\""))
    }

    @Test
    fun listModelsStripsPrefix() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[{"name":"models/gemini-a"},{"name":"models/gemini-b"}]}"""),
        )

        val models = provider.listModels(config())

        assertEquals(listOf("gemini-a", "gemini-b"), models)
        assertTrue(server.takeRequest().path.orEmpty().contains("key=AIza-test"))
    }
}
