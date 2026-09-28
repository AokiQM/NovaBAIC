package com.verlintas.baic2.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String = "{}",
    val readOnly: Boolean = false,
    val danger: DangerLevel = DangerLevel.LOW,
    val parallelSafe: Boolean = false,
)

@Serializable
enum class DangerLevel {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class ProviderError(
    val kind: Kind,
    val message: String,
    val httpStatus: Int? = null,
    val retryAfterSeconds: Int? = null,
) {
    @Serializable
    enum class Kind {
        NETWORK,
        TIMEOUT,
        AUTH,
        RATE_LIMIT,
        SERVER,
        INVALID_REQUEST,
        UNKNOWN,
    }
}

/** Provider-level streaming events: a thin, vendor-neutral wire model. */
sealed interface StreamEvent {
    data class TextDelta(val text: String) : StreamEvent

    data class ThinkingDelta(val text: String) : StreamEvent

    data class ToolCallsDone(val calls: List<ToolCall>) : StreamEvent

    data class Usage(val promptTokens: Long?, val completionTokens: Long?) : StreamEvent

    data object Done : StreamEvent

    data class Failed(val error: ProviderError) : StreamEvent
}

data class ChatRequest(
    val config: ProviderConfig,
    val systemPrompt: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec> = emptyList(),
)

/**
 * A model backend. Implementations translate vendor wires into
 * [StreamEvent]; failures are typed, never stringly.
 */
interface ChatProvider {
    fun stream(request: ChatRequest): Flow<StreamEvent>

    suspend fun listModels(config: ProviderConfig): List<String> = emptyList()
}
