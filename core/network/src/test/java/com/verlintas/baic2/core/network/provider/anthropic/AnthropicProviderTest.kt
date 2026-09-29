/*
 * Copyright (C) 2026 Verlintas
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This file is part of BetterAIChat2.
 *
 * BetterAIChat2 is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * BetterAIChat2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * BetterAIChat2. If not, see <https://www.gnu.org/licenses/>.
 */

package com.verlintas.baic2.core.network.provider.anthropic

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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AnthropicProviderTest {

    private lateinit var server: MockWebServer
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val provider = AnthropicProvider(OkHttpClient(), json)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun config(reasoning: Boolean = false) = ProviderConfig(
        provider = ProviderId.ANTHROPIC,
        baseUrl = server.url("").toString().trimEnd('/'),
        apiKey = "sk-ant-test",
        model = "claude-test",
        reasoning = reasoning,
    )

    private fun request(
        messages: List<ChatMessage> = listOf(ChatMessage(role = ChatRole.USER, content = "hi")),
        tools: List<ToolSpec> = emptyList(),
        reasoning: Boolean = false,
    ) = ChatRequest(
        config = config(reasoning),
        systemPrompt = "be nice",
        messages = messages,
        tools = tools,
    )

    private fun sse(vararg events: Pair<String, String>): String = buildString {
        events.forEach { (type, data) ->
            append("event: ").append(type).append('\n')
            append("data: ").append(data).append("\n\n")
        }
    }

    private fun enqueueSse(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )
    }

    @Test
    fun streamsTextDeltasUsageAndDone() = runTest {
        enqueueSse(
            sse(
                "message_start" to """{"type":"message_start","message":{"usage":{"input_tokens":25,"output_tokens":1}}}""",
                "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hel"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lo"}}""",
                "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":15}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )

        val events = provider.stream(request()).toList()

        assertEquals(
            listOf(
                StreamEvent.Usage(25, null),
                StreamEvent.TextDelta("Hel"),
                StreamEvent.TextDelta("lo"),
                StreamEvent.Usage(null, 15),
                StreamEvent.Done,
            ),
            events,
        )
    }

    @Test
    fun assemblesToolUseFromJsonFragments() = runTest {
        enqueueSse(
            sse(
                "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"get_weather"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"ci"}}""",
                "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"ty\":\"SF\"}"}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )

        val events = provider.stream(request()).toList()
        val calls = events.filterIsInstance<StreamEvent.ToolCallsDone>().single().calls

        assertEquals(1, calls.size)
        assertEquals("toolu_1", calls[0].id)
        assertEquals("get_weather", calls[0].name)
        assertEquals("""{"city":"SF"}""", calls[0].argumentsJson)
    }

    @Test
    fun mapsThinkingDeltas() = runTest {
        enqueueSse(
            sse(
                "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"thinking_delta","thinking":"hmm"}}""",
                "message_stop" to """{"type":"message_stop"}""",
            ),
        )

        val events = provider.stream(request()).toList()

        assertTrue(events.contains(StreamEvent.ThinkingDelta("hmm")))
    }

    @Test
    fun mapsAuthError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""),
        )

        val events = provider.stream(request()).toList()
        val failure = events.filterIsInstance<StreamEvent.Failed>().single()

        assertEquals(ProviderError.Kind.AUTH, failure.error.kind)
        assertEquals("invalid x-api-key", failure.error.message)
    }

    @Test
    fun buildsRequestWithSystemMergedToolResultsAndHeaders() = runTest {
        enqueueSse(sse("message_stop" to """{"type":"message_stop"}"""))
        val history = listOf(
            ChatMessage(role = ChatRole.SYSTEM, content = "remember: be brief"),
            ChatMessage(role = ChatRole.USER, content = "weather?"),
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "",
                toolCalls = listOf(
                    ToolCall(id = "toolu_9", name = "get_weather", argumentsJson = """{"city":"SF"}"""),
                ),
            ),
            ChatMessage(
                role = ChatRole.TOOL,
                content = "18C",
                toolCallId = "toolu_9",
                toolName = "get_weather",
            ),
        )

        provider.stream(
            request(
                messages = history,
                tools = listOf(
                    ToolSpec(
                        name = "get_weather",
                        description = "weather",
                        parametersJson = """{"type":"object","properties":{"city":{"type":"string"}}}""",
                    ),
                ),
            ),
        ).toList()

        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("sk-ant-test", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))

        val body = json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertTrue(body.getValue("system").jsonPrimitive.content.contains("be nice"))
        assertTrue(body.getValue("system").jsonPrimitive.content.contains("be brief"))

        val messages = body.getValue("messages")
        assertTrue(messages.toString().contains("tool_use"))
        assertTrue(messages.toString().contains("tool_result"))

        val tools = body.getValue("tools")
        assertTrue(tools.toString().contains("get_weather"))
    }

    @Test
    fun retriesServerErrorOnce() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0").setBody("busy"))
        enqueueSse(sse("message_stop" to """{"type":"message_stop"}"""))

        val events = provider.stream(request()).toList()

        assertEquals(StreamEvent.Done, events.last())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun mapsImageAttachmentToImageBlock() = runTest {
        enqueueSse(sse("message_stop" to """{"type":"message_stop"}"""))
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
        val content = body.getValue("messages").toString()
        assertTrue(content.contains("\"type\":\"image\""))
        assertTrue(content.contains("\"media_type\":\"image/jpeg\""))
        assertTrue(content.contains("\"data\":\"QUJD\""))
    }

    @Test
    fun listModelsParsesIds() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"claude-a"},{"id":"claude-b"}]}"""),
        )

        val models = provider.listModels(config())

        assertEquals(listOf("claude-a", "claude-b"), models)
    }

    @Test
    fun replaysThinkingSignatureAndOmitsTemperatureWhenReasoning() = runTest {
        enqueueSse(sse("message_stop" to "{}"))
        val history = listOf(
            ChatMessage(role = ChatRole.USER, content = "why?"),
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "because",
                thinking = "step by step",
                thinkingSignature = "sig-123",
            ),
        )

        provider.stream(request(messages = history, reasoning = true)).toList()

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertTrue(
            body["temperature"] == null || body["temperature"] is kotlinx.serialization.json.JsonNull,
            "temperature must be omitted while thinking is enabled",
        )
        val messages = body.getValue("messages").jsonArray
        val blocks = messages[1].jsonObject.getValue("content").jsonArray
        val first = blocks[0].jsonObject
        assertEquals("thinking", first.getValue("type").jsonPrimitive.content)
        assertEquals("step by step", first.getValue("thinking").jsonPrimitive.content)
        assertEquals("sig-123", first.getValue("signature").jsonPrimitive.content)
    }

    @Test
    fun dropsUnsignedThinkingWhenReasoningIsOff() = runTest {
        enqueueSse(sse("message_stop" to "{}"))
        val history = listOf(
            ChatMessage(role = ChatRole.USER, content = "why?"),
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = "because",
                thinking = "step by step",
                thinkingSignature = "sig-123",
            ),
        )

        provider.stream(request(messages = history)).toList()

        val body = json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val blocks = body.getValue("messages").jsonArray[1].jsonObject
            .getValue("content").jsonArray
        assertEquals("text", blocks[0].jsonObject.getValue("type").jsonPrimitive.content)
    }
}
