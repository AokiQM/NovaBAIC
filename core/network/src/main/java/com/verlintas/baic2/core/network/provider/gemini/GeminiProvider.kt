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

package com.verlintas.baic2.core.network.provider.gemini

import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.network.provider.BODY_LIMIT
import com.verlintas.baic2.core.network.provider.ProviderException
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Gemini adapter (`streamGenerateContent?alt=sse`).
 *
 * Gemini has no tool-call ids: synthetic ids (`gcall_*`) are generated here
 * and tool results are matched back by function name.
 */
class GeminiProvider(
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
            val toolCalls = mutableListOf<ToolCall>()

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
                    if (processEvent(event.data, toolCalls)) break
                }
                parser.endOfStream()?.let { processEvent(it.data, toolCalls) }
            }

            if (toolCalls.isNotEmpty()) emit(StreamEvent.ToolCallsDone(toolCalls))
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

    override suspend fun listModels(config: ProviderConfig): List<String> {
        val request = Request.Builder()
            .url(joinUrl(config.baseUrl, "v1beta/models") + "?key=" + config.apiKey)
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
            val text = response.body?.string() ?: return emptyList()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            val models = root?.get("models") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
            return models.mapNotNull { element ->
                ((element as? JsonObject)?.get("name") as? JsonPrimitive)
                    ?.content
                    ?.removePrefix("models/")
                    ?.takeIf { it.isNotBlank() }
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
        toolCalls: MutableList<ToolCall>,
    ): Boolean {
        if (data.isBlank()) return false
        val chunk = runCatching { json.decodeFromString(GeminiChunk.serializer(), data) }.getOrNull()
            ?: return false

        chunk.candidates.firstOrNull()?.let { candidate ->
            candidate.content?.parts?.forEach { part ->
                part.text?.takeIf { it.isNotEmpty() }?.let { text ->
                    if (part.thought == true) {
                        emit(StreamEvent.ThinkingDelta(text))
                    } else {
                        emit(StreamEvent.TextDelta(text))
                    }
                }
                part.functionCall?.let { call ->
                    toolCalls += ToolCall(
                        id = "gcall_${call.name}_${toolCalls.size}",
                        name = call.name,
                        argumentsJson = call.args?.toString() ?: "{}",
                    )
                }
            }
        }
        chunk.usage?.let { usage ->
            emit(StreamEvent.Usage(usage.promptTokenCount, usage.candidatesTokenCount))
        }
        return false
    }

    private fun buildHttpRequest(request: ChatRequest): Request {
        val payload = buildPayload(request)
        val body = json.encodeToString(GeminiRequest.serializer(), payload)
        val url = joinUrl(request.config.baseUrl, "v1beta/models") +
            "/${request.config.model}:streamGenerateContent?alt=sse&key=${request.config.apiKey}"
        return Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildPayload(request: ChatRequest): GeminiRequest {
        val systemText = buildList {
            if (request.systemPrompt.isNotBlank()) add(request.systemPrompt)
            request.messages.filter { it.role == ChatRole.SYSTEM }
                .map { it.content }
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }.joinToString("\n\n").ifBlank { null }

        val contents = mutableListOf<GeminiContent>()
        fun append(role: String, parts: List<GeminiPart>) {
            if (parts.isEmpty()) return
            val last = contents.lastOrNull()
            if (last != null && last.role == role) {
                contents[contents.lastIndex] = last.copy(parts = last.parts + parts)
            } else {
                contents += GeminiContent(role = role, parts = parts)
            }
        }

        request.messages.forEach { message ->
            when (message.role) {
                ChatRole.SYSTEM -> Unit
                ChatRole.USER -> append("user", userParts(message))
                ChatRole.ASSISTANT -> {
                    val parts = buildList {
                        if (message.content.isNotBlank()) add(GeminiPart(text = message.content))
                        message.toolCalls.forEach { call ->
                            add(
                                GeminiPart(
                                    functionCall = GeminiFunctionCall(
                                        name = call.name,
                                        args = parseJsonObject(call.argumentsJson),
                                    ),
                                ),
                            )
                        }
                    }
                    append("model", parts)
                }

                ChatRole.TOOL -> append(
                    "user",
                    listOf(
                        GeminiPart(
                            functionResponse = GeminiFunctionResponse(
                                name = message.toolName ?: "tool",
                                response = buildJsonObject {
                                    put("result", message.content)
                                },
                            ),
                        ),
                    ),
                )
            }
        }

        val tools = request.tools.takeIf { it.isNotEmpty() }?.let { specs ->
            listOf(
                GeminiTool(
                    functionDeclarations = specs.map { spec ->
                        GeminiFunctionDeclaration(
                            name = spec.name,
                            description = spec.description,
                            parameters = parseJsonObject(spec.parametersJson),
                        )
                    },
                ),
            )
        }

        return GeminiRequest(
            contents = contents,
            systemInstruction = systemText?.let { GeminiContent(parts = listOf(GeminiPart(text = it))) },
            tools = tools,
            generationConfig = GeminiGenerationConfig(
                temperature = request.config.temperature,
                maxOutputTokens = request.config.maxTokens,
            ),
        )
    }

    private fun userParts(message: com.verlintas.baic2.core.model.ChatMessage): List<GeminiPart> =
        buildList {
            val text = buildTextWithAttachments(message)
            if (text.isNotBlank()) {
                add(GeminiPart(text = text))
            }
            message.attachments
                .filter {
                    it.kind == com.verlintas.baic2.core.model.AttachmentKind.IMAGE && it.base64 != null
                }
                .forEach { image ->
                    add(
                        GeminiPart(
                            inlineData = GeminiInlineData(
                                mimeType = image.mimeType,
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

    private fun extractErrorMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val error = root["error"] as? JsonObject ?: return null
        (error["message"] as? JsonPrimitive)?.content
    }.getOrNull()

    private fun joinUrl(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("systemInstruction") val systemInstruction: GeminiContent? = null,
    val tools: List<GeminiTool>? = null,
    @SerialName("generationConfig") val generationConfig: GeminiGenerationConfig? = null,
)

@Serializable
private data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>,
)

@Serializable
private data class GeminiPart(
    val text: String? = null,
    val thought: Boolean? = null,
    @SerialName("functionCall") val functionCall: GeminiFunctionCall? = null,
    @SerialName("functionResponse") val functionResponse: GeminiFunctionResponse? = null,
    @SerialName("inlineData") val inlineData: GeminiInlineData? = null,
)

@Serializable
private data class GeminiInlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String,
)

@Serializable
private data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject? = null,
)

@Serializable
private data class GeminiFunctionResponse(
    val name: String,
    val response: JsonObject,
)

@Serializable
private data class GeminiTool(
    val functionDeclarations: List<GeminiFunctionDeclaration>,
)

@Serializable
private data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
private data class GeminiGenerationConfig(
    val temperature: Double,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
)

@Serializable
private data class GeminiChunk(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("usageMetadata") val usage: GeminiUsage? = null,
)

@Serializable
private data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

@Serializable
private data class GeminiUsage(
    @SerialName("promptTokenCount") val promptTokenCount: Long? = null,
    @SerialName("candidatesTokenCount") val candidatesTokenCount: Long? = null,
)
