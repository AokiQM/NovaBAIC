package com.verlintas.baic2.mcp

import com.verlintas.baic2.core.model.McpServer
import com.verlintas.baic2.core.model.McpToolInfo
import com.verlintas.baic2.core.network.sse.SseParser
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class McpSession(
    val server: McpServer,
    val sessionId: String?,
    val protocolVersion: String,
)

/**
 * Minimal MCP client over Streamable HTTP: JSON-RPC 2.0 requests, plain JSON
 * or SSE responses, session id propagation. Only the tools capability is used.
 */
@Singleton
class McpClient @Inject constructor(
    client: OkHttpClient,
    private val json: Json,
) {

    private val client = client.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(45, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val nextId = AtomicLong(1)

    suspend fun initialize(server: McpServer): Result<McpSession> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                server = server,
                method = "initialize",
                params = buildJsonObject {
                    put("protocolVersion", PROTOCOL_VERSION)
                    put("capabilities", buildJsonObject {})
                    put(
                        "clientInfo",
                        buildJsonObject {
                            put("name", "baic2")
                            put("version", "0.1.0")
                        },
                    )
                },
                sessionId = null,
                includeId = true,
            )
            val version = response.result.jsonObject["protocolVersion"]?.jsonPrimitive?.contentOrNull
                ?: PROTOCOL_VERSION
            val sessionId = response.sessionId
            // Best-effort initialized notification; servers that answer with
            // 202/empty bodies are fine.
            runCatching {
                request(
                    server = server,
                    method = "notifications/initialized",
                    params = buildJsonObject {},
                    sessionId = sessionId,
                    includeId = false,
                )
            }
            McpSession(server = server, sessionId = sessionId, protocolVersion = version)
        }
    }

    suspend fun listTools(session: McpSession): Result<List<McpToolInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = request(
                server = session.server,
                method = "tools/list",
                params = buildJsonObject {},
                sessionId = session.sessionId,
                includeId = true,
            )
            val result = response.result
            val tools = result.jsonObject["tools"]?.jsonArray ?: return@runCatching emptyList()
            tools.map { element ->
                val tool = element.jsonObject
                val annotations = tool["annotations"] as? JsonObject
                McpToolInfo(
                    name = tool["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    description = tool["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    inputSchemaJson = (tool["inputSchema"] ?: JsonObject(emptyMap())).toString(),
                    readOnly = (annotations?.get("readOnlyHint") as? JsonPrimitive)
                        ?.contentOrNull?.toBoolean() ?: false,
                )
            }.filter { it.name.isNotBlank() }
        }
    }

    suspend fun callTool(
        session: McpSession,
        name: String,
        argumentsJson: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val arguments = runCatching {
                json.parseToJsonElement(argumentsJson) as? JsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            val response = request(
                server = session.server,
                method = "tools/call",
                params = buildJsonObject {
                    put("name", name)
                    put("arguments", arguments)
                },
                sessionId = session.sessionId,
                includeId = true,
            )
            val result = response.result
            val isError = (result.jsonObject["isError"] as? JsonPrimitive)
                ?.contentOrNull?.toBoolean() == true
            val text = result.jsonObject["content"]?.jsonArray
                ?.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                        "text" -> obj["text"]?.jsonPrimitive?.contentOrNull
                        else -> obj.toString()
                    }
                }
                ?.joinToString("\n")
                .orEmpty()
            if (isError) {
                throw McpToolError(text.ifBlank { "MCP tool '$name' reported an error" })
            }
            text.ifBlank { "(empty result)" }
        }
    }

    private data class RpcResponse(
        val result: JsonObject,
        val sessionId: String?,
    )

    private fun request(
        server: McpServer,
        method: String,
        params: JsonObject,
        sessionId: String?,
        includeId: Boolean,
    ): RpcResponse {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            if (includeId) put("id", nextId.getAndIncrement())
            put("method", method)
            put("params", params)
        }
        val requestBuilder = Request.Builder()
            .url(server.url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
        server.headers.forEach { (key, value) -> requestBuilder.header(key, value) }
        sessionId?.let { requestBuilder.header("Mcp-Session-Id", it) }

        val httpResponse = client.newCall(requestBuilder.build()).execute()
        httpResponse.use {
            if (!httpResponse.isSuccessful) {
                throw IOException("HTTP ${httpResponse.code} from ${server.url}")
            }
            val returnedSession = httpResponse.header("Mcp-Session-Id")
            val contentType = httpResponse.header("Content-Type").orEmpty()
            val text = httpResponse.body?.string().orEmpty()
            if (contentType.contains("text/event-stream")) {
                val parser = SseParser()
                var payload: String? = null
                text.lines().forEach { line ->
                    val event = parser.line(line)
                    if (event != null && (event.data != "[DONE]")) {
                        payload = payload ?: event.data
                    }
                }
                parser.endOfStream()?.let { payload = payload ?: it.data }
                val data = payload ?: throw IOException("empty SSE response")
                return RpcResponse(parseRpc(data), returnedSession)
            }
            if (text.isBlank()) return RpcResponse(JsonObject(emptyMap()), returnedSession)
            return RpcResponse(parseRpc(text), returnedSession)
        }
    }

    private fun parseRpc(text: String): JsonObject {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: throw IOException("malformed JSON-RPC response")
        val error = root["error"] as? JsonObject
        if (error != null) {
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "MCP error"
            val code = (error["code"] as? JsonPrimitive)?.contentOrNull
            throw IOException("MCP error $code: $message")
        }
        return (root["result"] as? JsonObject) ?: JsonObject(emptyMap())
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class McpToolError(message: String) : Exception(message)
