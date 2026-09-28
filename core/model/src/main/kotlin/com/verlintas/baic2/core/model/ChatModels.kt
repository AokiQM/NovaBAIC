package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Conversation(
    val id: Long = 0L,
    val title: String = "",
    val agentId: Long? = null,
    val mode: AppMode = AppMode.CHAT,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Serializable
data class ChatMessage(
    val id: Long = 0L,
    val conversationId: Long = 0L,
    val role: ChatRole,
    val content: String = "",
    val thinking: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val model: String? = null,
    val createdAt: Long = 0L,
)

@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String = "{}",
    val result: String? = null,
    val status: ToolCallStatus = ToolCallStatus.PENDING,
)

@Serializable
enum class ToolCallStatus {
    PENDING,
    RUNNING,
    DONE,
    FAILED,
    DENIED,
    REJECTED,
}
