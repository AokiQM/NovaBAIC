package com.verlintas.baic2.core.data.repository

import androidx.room.withTransaction
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.Conversation
import com.verlintas.baic2.core.model.ConversationPreview
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ConversationRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeConversations(): Flow<List<ConversationPreview>> =
        db.conversationDao().observeSummaries().map { list ->
            list.map { summary ->
                ConversationPreview(
                    conversation = mapper.conversationToModel(summary.conversation),
                    lastMessage = summary.lastMessage,
                )
            }
        }

    fun observeConversation(id: Long): Flow<Conversation?> =
        db.conversationDao().observeById(id).map { entity -> entity?.let(mapper::conversationToModel) }

    fun observeMessages(conversationId: Long): Flow<List<ChatMessage>> =
        db.messageDao().observeByConversation(conversationId)
            .map { list -> list.map(mapper::messageToModel) }

    suspend fun get(id: Long): Conversation? =
        db.conversationDao().getById(id)?.let(mapper::conversationToModel)

    suspend fun getMessages(conversationId: Long): List<ChatMessage> =
        db.messageDao().getByConversation(conversationId).map(mapper::messageToModel)

    suspend fun create(agentId: Long?, mode: AppMode, title: String): Long {
        val now = System.currentTimeMillis()
        return db.conversationDao().insert(
            mapper.conversationToEntity(
                Conversation(title = title, agentId = agentId, mode = mode, createdAt = now, updatedAt = now),
            ),
        )
    }

    suspend fun append(message: ChatMessage): Long = db.withTransaction {
        val id = db.messageDao().insert(mapper.messageToEntity(message))
        db.conversationDao().touch(message.conversationId, System.currentTimeMillis())
        id
    }

    suspend fun updateAssistantContent(messageId: Long, content: String) {
        db.messageDao().updateContent(messageId, content)
    }

    suspend fun updateToolCalls(messageId: Long, toolCalls: List<com.verlintas.baic2.core.model.ToolCall>) {
        db.messageDao().updateToolCalls(
            messageId,
            mapper.encodeToolCalls(toolCalls),
        )
    }

    suspend fun updateTitle(id: Long, title: String) {
        db.conversationDao().updateTitle(id, title, System.currentTimeMillis())
    }

    suspend fun updateMeta(id: Long, agentId: Long?, mode: AppMode) {
        db.conversationDao().updateMeta(id, agentId, mode.name, System.currentTimeMillis())
    }

    suspend fun delete(id: Long) = db.withTransaction {
        db.messageDao().deleteAllForConversation(id)
        db.conversationDao().delete(id)
    }

    suspend fun clearMessages(id: Long) {
        db.messageDao().deleteAllForConversation(id)
    }

    suspend fun deleteMessagesAfter(conversationId: Long, afterMessageId: Long) {
        db.messageDao().deleteAfter(conversationId, afterMessageId)
    }
}
