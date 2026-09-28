package com.verlintas.baic2.core.data.mapper

import com.verlintas.baic2.core.data.db.AgentEntity
import com.verlintas.baic2.core.data.db.ConversationEntity
import com.verlintas.baic2.core.data.db.MemoryEntity
import com.verlintas.baic2.core.data.db.MessageEntity
import com.verlintas.baic2.core.data.db.RunEntity
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.Conversation
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.MemoryKind
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.Run
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.ToolCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class ChatMapper @Inject constructor(private val json: Json) {

    fun agentToModel(entity: AgentEntity): Agent = Agent(
        id = entity.id,
        name = entity.name,
        provider = enumOf(entity.provider, ProviderId.OPENAI_COMPATIBLE),
        baseUrl = entity.baseUrl,
        model = entity.model,
        temperature = entity.temperature,
        maxTokens = entity.maxTokens,
        reasoning = entity.reasoning,
        systemPrompt = entity.systemPrompt,
        isDefault = entity.isDefault,
    )

    fun agentToEntity(agent: Agent, encryptedApiKey: String, createdAt: Long): AgentEntity = AgentEntity(
        id = agent.id,
        name = agent.name,
        provider = agent.provider.name,
        baseUrl = agent.baseUrl,
        model = agent.model,
        temperature = agent.temperature,
        maxTokens = agent.maxTokens,
        reasoning = agent.reasoning,
        systemPrompt = agent.systemPrompt,
        encryptedApiKey = encryptedApiKey,
        isDefault = agent.isDefault,
        createdAt = createdAt,
    )

    fun conversationToModel(entity: ConversationEntity): Conversation = Conversation(
        id = entity.id,
        title = entity.title,
        agentId = entity.agentId,
        mode = enumOf(entity.mode, AppMode.CHAT),
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    fun conversationToEntity(conversation: Conversation): ConversationEntity = ConversationEntity(
        id = conversation.id,
        title = conversation.title,
        agentId = conversation.agentId,
        mode = conversation.mode.name,
        createdAt = conversation.createdAt,
        updatedAt = conversation.updatedAt,
    )

    fun messageToModel(entity: MessageEntity): ChatMessage = ChatMessage(
        id = entity.id,
        conversationId = entity.conversationId,
        role = enumOf(entity.role, ChatRole.USER),
        content = entity.content,
        thinking = entity.thinking,
        toolCalls = decodeToolCalls(entity.toolCallsJson),
        attachments = decodeAttachments(entity.attachmentsJson),
        toolCallId = entity.toolCallId,
        toolName = entity.toolName,
        model = entity.model,
        createdAt = entity.createdAt,
        starred = entity.starred,
    )

    fun messageToEntity(message: ChatMessage): MessageEntity = MessageEntity(
        id = message.id,
        conversationId = message.conversationId,
        role = message.role.name,
        content = message.content,
        thinking = message.thinking,
        toolCallsJson = encodeToolCalls(message.toolCalls),
        attachmentsJson = encodeAttachments(message.attachments),
        toolCallId = message.toolCallId,
        toolName = message.toolName,
        model = message.model,
        createdAt = message.createdAt,
        starred = message.starred,
    )

    /** Ephemeral base64 payloads are never persisted. */
    fun encodeAttachments(attachments: List<com.verlintas.baic2.core.model.Attachment>): String =
        json.encodeToString(attachments.map { it.copy(base64 = null) })

    private fun decodeAttachments(raw: String): List<com.verlintas.baic2.core.model.Attachment> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<com.verlintas.baic2.core.model.Attachment>>(raw)
        }.getOrDefault(emptyList())
    }

    fun memoryToModel(entity: MemoryEntity): Memory = Memory(
        id = entity.id,
        kind = enumOf(entity.kind, MemoryKind.MEMORY),
        content = entity.content,
        conversationId = entity.conversationId,
        createdAt = entity.createdAt,
    )

    fun encodeToolCalls(toolCalls: List<ToolCall>): String = json.encodeToString(toolCalls)

    fun runToModel(entity: RunEntity): Run = Run(
        id = entity.id,
        conversationId = entity.conversationId,
        mode = enumOf(entity.mode, AppMode.CHAT),
        state = enumOf(entity.state, RunState.RUNNING),
        startedAt = entity.startedAt,
        updatedAt = entity.updatedAt,
    )

    private fun decodeToolCalls(raw: String): List<ToolCall> {
        if (raw.isBlank()) return emptyList()
        // Corrupted history must never crash the app; an empty list keeps the
        // conversation readable and re-sendable.
        return runCatching { json.decodeFromString<List<ToolCall>>(raw) }.getOrDefault(emptyList())
    }

    private inline fun <reified T : Enum<T>> enumOf(value: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback
}
