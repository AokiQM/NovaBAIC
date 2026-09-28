package com.verlintas.baic2.core.engine

import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec

sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent

    data class ThinkingDelta(val text: String) : AgentEvent

    data class RoundStarted(val round: Int) : AgentEvent

    data class AssistantMessage(val message: ChatMessage) : AgentEvent

    data class ToolCallStarted(val call: ToolCall) : AgentEvent

    data class ToolCallFinished(val call: ToolCall) : AgentEvent

    data class Usage(val promptTokens: Long?, val completionTokens: Long?) : AgentEvent

    data object Completed : AgentEvent

    data class Failed(val error: AgentFailure) : AgentEvent
}

data class AgentFailure(
    val kind: Kind,
    val message: String,
) {
    enum class Kind {
        PROVIDER,
        BUDGET,
        UNSUPPORTED_PROVIDER,
        INTERNAL,
    }
}
