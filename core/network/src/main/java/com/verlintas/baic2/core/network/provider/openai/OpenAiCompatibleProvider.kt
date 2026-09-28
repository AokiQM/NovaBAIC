package com.verlintas.baic2.core.network.provider.openai

import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.network.provider.ProviderException
import com.verlintas.baic2.core.network.sse.SseParser
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.EncodeDefault
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
import okhttp3.Response

/**
 * OpenAI Chat Completions compatible adapter (OpenAI, DeepSeek, Moonshot,
 * Qwen, Ollama, gateways...).
 *
 * Blocking IO: collect this flow on [kotlinx.coroutines.Dispatchers.IO].
 * Cancelling the collector cancels the in-flight HTTP call.
 */
class OpenAiCompatibleProvider(
    private val client: OkHttpClient,
    private val json: Json,
) : ChatProvider {

    override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        val job = currentCoroutineContext()[Job]
        var currentCall: Call? = null
        val cancelCall = job?.invokeOnCompletion { currentCall?.cancel() }

        try {
            val (call, response) = executeWithRetry { buildHttpRequest(request) }
            currentCall = call

            val body = response.body
            if (!response.isSuccessful || body == null) {
                val errorBody = runCatching { body?.string() }.getOrNull().orEmpty().take(BODY_LIMIT)
                emit(StreamEvent.Failed(mapHttpError(response.code, response.header("Retry-After"), errorBody)))
                return@flow
            }

            val parser = SseParser()
            val toolCalls = LinkedHashMap<Int, ToolCallAccumulator>()

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

            val calls = toolCalls.values.map { it.toToolCall() }
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

    override suspend fun listModels(config: ProviderConfig): List<String> {
        val request = Request.Builder()
            .url(joinUrl(config.baseUrl, "models"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .get()
            .build()
        val call = client.newCall(request)
        val job = currentCoroutineContext()[Job]
        val cancelCall = job?.invokeOnCompletion { call.cancel() }
        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull().orEmpty().take(BODY_LIMIT)
                throw ProviderException(mapHttpError(response.code, response.header("Retry-After"), body))
            }
            val text = response.body?.string() ?: return emptyList()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            val data = root?.get("data") as? JsonArray ?: return emptyList()
            return data.mapNotNull { element ->
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

    /** Emits deltas/usage directly; returns true when the stream is terminal. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamEvent>.processEvent(
        data: String,
        toolCalls: MutableMap<Int, ToolCallAccumulator>,
    ): Boolean {
        if (data == DONE_MARKER) return true
        if (data.isBlank()) return false

        val chunk = runCatching { json.decodeFromString(OpenAiChunk.serializer(), data) }.getOrNull()
            ?: return false

        chunk.choices.firstOrNull()?.delta?.let { delta ->
            delta.content?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.TextDelta(it)) }
            delta.reasoning_content?.takeIf { it.isNotEmpty() }?.let { emit(StreamEvent.ThinkingDelta(it)) }
            delta.tool_calls?.forEach { fragment ->
                val acc = toolCalls.getOrPut(fragment.index) { ToolCallAccumulator() }
                fragment.id?.let { acc.id = it }
                fragment.function?.name?.let { acc.name = it }
                fragment.function?.arguments?.let { acc.arguments.append(it) }
            }
        }
        chunk.usage?.let { emit(StreamEvent.Usage(it.promptTokens, it.completionTokens)) }
        return false
    }

    private fun buildHttpRequest(request: ChatRequest): Request {
        val body = json.encodeToString(WireRequest.serializer(), buildPayload(request))
        return Request.Builder()
            .url(joinUrl(request.config.baseUrl, "chat/completions"))
            .header("Authorization", "Bearer ${request.config.apiKey}")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    private fun buildPayload(request: ChatRequest): WireRequest {
        val messages = buildList {
            if (request.systemPrompt.isNotBlank()) {
                add(WireMessage(role = "system", content = request.systemPrompt))
            }
            request.messages.forEach { message ->
                when (message.role) {
                    ChatRole.SYSTEM -> add(WireMessage(role = "system", content = message.content))
                    ChatRole.USER -> add(WireMessage(role = "user", content = message.content))
                    ChatRole.TOOL -> add(
                        WireMessage(
                            role = "tool",
                            content = message.content,
                            toolCallId = message.toolCallId,
                        ),
                    )
                    ChatRole.ASSISTANT -> add(
                        WireMessage(
                            role = "assistant",
                            content = message.content.ifEmpty { null },
                            toolCalls = message.toolCalls
                                .takeIf { it.isNotEmpty() }
                                ?.map { call ->
                                    WireToolCall(
                                        id = call.id,
                                        function = WireFunction(call.name, call.argumentsJson),
                                    )
                                },
                        ),
                    )
                }
            }
        }
        val tools = request.tools.takeIf { it.isNotEmpty() }?.map { spec ->
            WireTool(
                function = WireToolDef(
                    name = spec.name,
                    description = spec.description,
                    parameters = parseParameters(spec.parametersJson),
                ),
            )
        }
        return WireRequest(
            model = request.config.model,
            temperature = request.config.temperature,
            maxTokens = request.config.maxTokens,
            messages = messages,
            tools = tools,
        )
    }

    private fun parseParameters(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

    private suspend fun executeWithRetry(requestBuilder: () -> Request): Pair<Call, Response> {
        var attempt = 0
        while (true) {
            try {
                val call = client.newCall(requestBuilder())
                val response = call.execute()
                if (response.isSuccessful || attempt >= 1 || !isRetryable(response.code)) {
                    return call to response
                }
                val retryAfter = response.header("Retry-After")?.toLongOrNull()?.coerceIn(0, 10) ?: 1
                response.close()
                delay(retryAfter * 1_000)
                attempt++
            } catch (e: IOException) {
                if (attempt >= 1) throw e
                delay(RETRY_DELAY_MS)
                attempt++
            }
        }
    }

    private fun isRetryable(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    private fun mapHttpError(code: Int, retryAfterHeader: String?, body: String): ProviderError {
        val kind = when {
            code == 401 || code == 403 -> ProviderError.Kind.AUTH
            code == 429 -> ProviderError.Kind.RATE_LIMIT
            code in 400..499 -> ProviderError.Kind.INVALID_REQUEST
            code >= 500 -> ProviderError.Kind.SERVER
            else -> ProviderError.Kind.UNKNOWN
        }
        return ProviderError(
            kind = kind,
            message = extractErrorMessage(body) ?: "HTTP $code",
            httpStatus = code,
            retryAfterSeconds = retryAfterHeader?.toIntOrNull(),
        )
    }

    private fun extractErrorMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val error = root["error"] as? JsonObject ?: return null
        (error["message"] as? JsonPrimitive)?.content
    }.getOrNull()

    private fun mapIOException(e: IOException): ProviderError {
        val kind = if (e is SocketTimeoutException) ProviderError.Kind.TIMEOUT else ProviderError.Kind.NETWORK
        return ProviderError(kind = kind, message = e.message ?: e.javaClass.simpleName)
    }

    private fun joinUrl(baseUrl: String, path: String): String =
        baseUrl.trimEnd('/') + "/" + path.trimStart('/')

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun toToolCall(): ToolCall = ToolCall(
            id = id.ifEmpty { "call_${name}_${arguments.length}" },
            name = name,
            argumentsJson = arguments.toString().ifBlank { "{}" },
        )
    }

    private companion object {
        const val DONE_MARKER = "[DONE]"
        const val BODY_LIMIT = 2_000
        const val RETRY_DELAY_MS = 500L
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class WireRequest(
    val model: String,
    @EncodeDefault val stream: Boolean = true,
    val temperature: Double,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val messages: List<WireMessage>,
    val tools: List<WireTool>? = null,
)

@Serializable
private data class WireMessage(
    val role: String,
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<WireToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
private data class WireToolCall(
    val id: String,
    @EncodeDefault val type: String = "function",
    val function: WireFunction,
)

@Serializable
private data class WireFunction(
    val name: String,
    val arguments: String,
)

@Serializable
private data class WireTool(
    @EncodeDefault val type: String = "function",
    val function: WireToolDef,
)

@Serializable
private data class WireToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
private data class OpenAiChunk(
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
) {
    @Serializable
    data class Choice(
        val delta: Delta? = null,
        @SerialName("finish_reason") val finishReason: String? = null,
    )

    @Serializable
    data class Delta(
        val content: String? = null,
        @SerialName("reasoning_content") val reasoning_content: String? = null,
        @SerialName("tool_calls") val tool_calls: List<ToolCallDelta>? = null,
    )

    @Serializable
    data class ToolCallDelta(
        val index: Int = 0,
        val id: String? = null,
        val function: FunctionDelta? = null,
    ) {
        @Serializable
        data class FunctionDelta(
            val name: String? = null,
            val arguments: String? = null,
        )
    }

    @Serializable
    data class Usage(
        @SerialName("prompt_tokens") val promptTokens: Long? = null,
        @SerialName("completion_tokens") val completionTokens: Long? = null,
    )
}
