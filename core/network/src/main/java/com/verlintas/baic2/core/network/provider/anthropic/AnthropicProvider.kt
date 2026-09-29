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

import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.network.provider.BODY_LIMIT
import com.verlintas.baic2.core.network.provider.ProviderException
import com.verlintas.baic2.core.network.provider.ToolCallHistory
import com.verlintas.baic2.core.network.provider.executeWithRetry
import com.verlintas.baic2.core.network.provider.mapHttpError
import com.verlintas.baic2.core.network.provider.mapIOException
import com.verlintas.baic2.core.network.sse.SseParser
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Anthropic Messages API adapter (Claude).
 *
 * Blocking IO: collect on [Dispatchers.IO]; cancelling the collector cancels
 * the in-flight call.
 */
class AnthropicProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : ChatProvider {

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        val job = currentCoroutineContext()[Job]
        var currentCall: Call? = null
        val cancelCall = job?.invokeOnCompletion { currentCall?.cancel() }

        try {
            val (call, response) = client.executeWithRetry { buildHttpRequest(request) }
            currentCall = call

            val body = response.body
            if (!response.isSuccessful || body == null) {
                val errorBody = runCatching { body?.string() }.getOrNull().orEmpty().take(BODY_LIMIT)
                emit(
                    StreamEvent.Failed(
                        mapHttpError(
                            code = response.code,
                            retryAfterHeader = response.header("Retry-After"),
                            message = extractErrorMessage(errorBody),
                        ),
                    ),
                )
                return@flow
            }

            val parser = SseParser()
            val toolBlocks = LinkedHashMap<Int, ToolBlockAccumulator>()

            body.source().use { source ->
                while (true) {
                    val line = try {
                        source.readUtf8Line() ?: break
                    } catch (e: IOException) {
                        if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
                        emit(StreamEvent.Failed(mapIOException(e)))
                        return@flow
                    }
                    val event = parser.line(line) ?: continue
                    if (processEvent(event.data, toolBlocks)) break
                }
                parser.endOfStream()?.let { processEvent(it.data, toolBlocks) }
            }

            val calls = toolBlocks.values.map { it.toToolCall() }
            if (calls.isNotEmpty()) emit(StreamEvent.ToolCallsDone(calls))
            emit(StreamEvent.Done)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (!currentCoroutineContext().isActive) throw CancellationException("cancelled")
            emit(StreamEvent.Failed(mapIOException(e)))
        } finally {
            cancelCall?.dispose()
            currentCall?.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun listModels(config: ProviderConfig): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(joinUrl(config.baseUrl, "v1/models"))
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", API_VERSION)
            .get()
            .build()
        val call = client.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancelCall = job?.invokeOnCompletion { call.cancel() }
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull().orEmpty().take(BODY_LIMIT)
                throw ProviderException(
                    mapHttpError(response.code, response.header("Retry-After"), extractErrorMessage(body)),
                )
            }
            val text = response.body?.string() ?: return@withContext emptyList()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            val data = root?.get("data") as? JsonArray ?: return@withContext emptyList()
            return@withContext data.mapNotNull { element ->
                ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw ProviderException(mapIOException(e))
        } finally {
            cancelCall?.dispose()
            call.cancel()
        }
    }

    /** Returns true when the stream is terminal. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.processEvent(
        data: String,
        toolBlocks: MutableMap<Int, ToolBlockAccumulator>,
    ): Boolean {
        if (data.isBlank()) return false
        val event = runCatching { json.decodeFromString(AnthropicEvent.serializer(), data) }.getOrNull()
            ?: return false

        when (event.type) {
            "content_block_start" -> {
                val index = event.index ?: return false
                val block = event.contentBlock
                if (block?.type == "tool_use") {
                    toolBlocks[index] = ToolBlockAccumulator(
                        id = block.id.orEmpty(),
                        name = block.name.orEmpty(),
                    )
                }
            }

            "content_block_delta" -> {
                val delta = event.delta ?: return false
                when (delta.type) {
                    "text_delta" -> delta.text?.takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamEvent.TextDelta(it)) }

                    "thinking_delta" -> delta.thinking?.takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamEvent.ThinkingDelta(it)) }

                    "signature_delta" -> delta.signature?.takeIf { it.isNotEmpty() }
                        ?.let { emit(StreamEvent.ThinkingSignature(it)) }

                    "input_json_delta" -> {
                        val index = event.index ?: return false
                        delta.partialJson?.let { fragment ->
                            toolBlocks.getOrPut(index) { ToolBlockAccumulator() }.arguments.append(fragment)
                        }
                    }
                }
            }

            "message_start" -> {
                event.message?.usage?.inputTokens?.let { emit(StreamEvent.Usage(it, null)) }
            }

            "message_delta" -> {
                event.usage?.outputTokens?.let { emit(StreamEvent.Usage(null, it)) }
            }

            "message_stop" -> return true

            "error" -> {
                val message = event.error?.message ?: "Anthropic stream error"
                emit(
                    StreamEvent.Failed(
                        mapHttpError(
                            code = event.error?.code ?: 500,
                            retryAfterHeader = null,
                            message = message,
                        ),
                    ),
                )
                return true
            }
        }
        return false
    }

    private fun buildHttpRequest(request: ChatRequest): Request {
        val payload = buildPayload(request)
        val body = json.encodeToString(AnthropicRequest.serializer(), payload)
        return Request.Builder()
            .url(joinUrl(request.config.baseUrl, "v1/messages"))
            .header("x-api-key", request.config.apiKey)
            .header("anthropic-version", API_VERSION)
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildPayload(request: ChatRequest): AnthropicRequest {
        val system = buildList {
            if (request.systemPrompt.isNotBlank()) add(request.systemPrompt)
            request.messages.filter { it.role == ChatRole.SYSTEM }
                .map { it.content }
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }.joinToString("\n\n").ifBlank { null }

        val messages = mutableListOf<AnthropicMessage>()
        fun append(role: String, blocks: List<AnthropicBlock>) {
            if (blocks.isEmpty()) return
            val last = messages.lastOrNull()
            if (last != null && last.role == role) {
                messages[messages.lastIndex] = last.copy(content = last.content + blocks)
            } else {
                messages += AnthropicMessage(role = role, content = blocks)
            }
        }

        ToolCallHistory.sanitize(request.messages).forEach { message ->
            when (message.role) {
                ChatRole.SYSTEM -> Unit
                ChatRole.USER -> append("user", userBlocks(message))
                ChatRole.ASSISTANT -> {
                    val blocks = buildList {
                        // Extended thinking must be replayed verbatim (with its
                        // signature) as the first block while thinking is on.
                        if (request.config.reasoning) {
                            val signature = message.thinkingSignature
                            val thinkingText = message.thinking
                            if (!signature.isNullOrBlank() && !thinkingText.isNullOrBlank()) {
                                add(
                                    AnthropicBlock(
                                        type = "thinking",
                                        thinking = thinkingText,
                                        signature = signature,
                                    ),
                                )
                            }
                        }
                        if (message.content.isNotBlank()) {
                            add(AnthropicBlock(type = "text", text = message.content))
                        }
                        message.toolCalls.forEach { call ->
                            add(
                                AnthropicBlock(
                                    type = "tool_use",
                                    id = call.id,
                                    name = call.name,
                                    input = parseJsonObject(call.argumentsJson),
                                ),
                            )
                        }
                    }
                    append("assistant", blocks)
                }

                ChatRole.TOOL -> append(
                    "user",
                    listOf(
                        AnthropicBlock(
                            type = "tool_result",
                            toolUseId = message.toolCallId,
                            content = message.content,
                        ),
                    ),
                )
            }
        }

        val tools = request.tools.takeIf { it.isNotEmpty() }?.map { spec ->
            AnthropicTool(
                name = spec.name,
                description = spec.description,
                inputSchema = parseToolSchema(spec.parametersJson),
            )
        }

        return AnthropicRequest(
            model = request.config.model,
            maxTokens = request.config.maxTokens ?: DEFAULT_MAX_TOKENS,
            // Thinking requires temperature to be unset (Anthropic pins it to 1).
            temperature = if (request.config.reasoning) null else request.config.temperature,
            system = system,
            messages = messages,
            tools = tools,
            thinking = if (request.config.reasoning) {
                AnthropicThinking(type = "enabled", budgetTokens = THINKING_BUDGET)
            } else {
                null
            },
        )
    }

    private fun userBlocks(message: com.verlintas.baic2.core.model.ChatMessage): List<AnthropicBlock> =
        buildList {
            val text = buildTextWithAttachments(message)
            if (text.isNotBlank()) {
                add(AnthropicBlock(type = "text", text = text))
            }
            message.attachments
                .filter {
                    it.kind == com.verlintas.baic2.core.model.AttachmentKind.IMAGE && it.base64 != null
                }
                .forEach { image ->
                    add(
                        AnthropicBlock(
                            type = "image",
                            source = AnthropicImageSource(
                                mediaType = image.mimeType,
                                data = image.base64.orEmpty(),
                            ),
                        ),
                    )
                }
        }

    private fun buildTextWithAttachments(
        message: com.verlintas.baic2.core.model.ChatMessage,
    ): String = buildString {
        append(message.content)
        message.attachments
            .filter { it.kind == com.verlintas.baic2.core.model.AttachmentKind.TEXT }
            .forEach { attachment ->
                attachment.text?.let { text ->
                    if (isNotBlank()) append("\n\n")
                    append("[附件: ").append(attachment.fileName ?: "text").append("]\n")
                    append(text)
                }
            }
    }.trim()

    private fun parseJsonObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

    private fun parseToolSchema(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }
            .getOrNull()
            ?.takeIf { it["type"] != null }
            ?: PERMISSIVE_TOOL_SCHEMA

    private fun extractErrorMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val error = root["error"] as? JsonObject ?: return null
        (error["message"] as? JsonPrimitive)?.content
    }.getOrNull()

    private fun joinUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        // Proxies often include /v1 in the base while we also add it.
        val effectivePath = if (base.endsWith("/v1") && path.startsWith("v1/")) {
            path.removePrefix("v1/")
        } else {
            path
        }
        return "$base/${effectivePath.trimStart('/')}"
    }

    private class ToolBlockAccumulator(
        var id: String = "",
        var name: String = "",
    ) {
        val arguments = StringBuilder()

        fun toToolCall(): ToolCall = ToolCall(
            id = id.ifEmpty { "toolu_${name}_${arguments.length}" },
            name = name,
            argumentsJson = arguments.toString().ifBlank { "{}" },
        )
    }

    private companion object {
        const val API_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 4096
        const val THINKING_BUDGET = 2048
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val temperature: Double? = null,
    val system: String? = null,
    val messages: List<AnthropicMessage>,
    val tools: List<AnthropicTool>? = null,
    @SerialName("thinking") val thinking: AnthropicThinking? = null,
    @SerialName("stream") val stream: Boolean = true,
)

@Serializable
private data class AnthropicThinking(
    val type: String,
    @SerialName("budget_tokens") val budgetTokens: Int,
)

@Serializable
private data class AnthropicMessage(
    val role: String,
    val content: List<AnthropicBlock>,
)

@Serializable
private data class AnthropicBlock(
    val type: String,
    val text: String? = null,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    val source: AnthropicImageSource? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: String? = null,
    val thinking: String? = null,
    val signature: String? = null,
)

@Serializable
private data class AnthropicImageSource(
    val type: String = "base64",
    @SerialName("media_type") val mediaType: String,
    val data: String,
)

@Serializable
private data class AnthropicTool(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject,
)

@Serializable
private data class AnthropicEvent(
    val type: String,
    val index: Int? = null,
    val delta: AnthropicDelta? = null,
    @SerialName("content_block") val contentBlock: AnthropicContentBlock? = null,
    val message: AnthropicMessageStart? = null,
    val usage: AnthropicUsage? = null,
    val error: AnthropicError? = null,
)

@Serializable
private data class AnthropicDelta(
    val type: String? = null,
    val text: String? = null,
    val thinking: String? = null,
    val signature: String? = null,
    @SerialName("partial_json") val partialJson: String? = null,
)

@Serializable
private data class AnthropicContentBlock(
    val type: String,
    val id: String? = null,
    val name: String? = null,
)

@Serializable
private data class AnthropicMessageStart(
    val usage: AnthropicUsage? = null,
)

@Serializable
private data class AnthropicUsage(
    @SerialName("input_tokens") val inputTokens: Long? = null,
    @SerialName("output_tokens") val outputTokens: Long? = null,
)

@Serializable
private data class AnthropicError(
    val type: String? = null,
    val message: String? = null,
    val code: Int? = null,
)

/**
 * Tools with a broken schema must never poison the whole request: fall back
 * to a permissive object schema the APIs accept.
 */
private val PERMISSIVE_TOOL_SCHEMA: JsonObject = Json.parseToJsonElement(
    """{"type":"object","properties":{}}""",
) as JsonObject
