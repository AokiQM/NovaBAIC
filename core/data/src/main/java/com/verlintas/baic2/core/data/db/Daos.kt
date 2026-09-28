package com.verlintas.baic2.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AgentDao {

    @Query("SELECT * FROM agents ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<AgentEntity>>

    @Query("SELECT * FROM agents WHERE id = :id")
    suspend fun getById(id: Long): AgentEntity?

    @Query("SELECT * FROM agents WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): AgentEntity?

    @Query("SELECT * FROM agents WHERE isDefault = 1 LIMIT 1")
    fun observeDefault(): Flow<AgentEntity?>

    @Insert
    suspend fun insert(entity: AgentEntity): Long

    @Update
    suspend fun update(entity: AgentEntity)

    @Query("SELECT COUNT(*) FROM agents")
    suspend fun count(): Int

    @Query("UPDATE agents SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE agents SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: Long)

    @Query("DELETE FROM agents WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT c.*, (
            SELECT m.content FROM messages m
            WHERE m.conversationId = c.id
            ORDER BY m.id DESC LIMIT 1
        ) AS lastMessage
        FROM conversations c
        ORDER BY c.updatedAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<ConversationSummary>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<ConversationEntity?>

    @Insert
    suspend fun insert(entity: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, now: Long)

    @Query("UPDATE conversations SET agentId = :agentId, mode = :mode, updatedAt = :now WHERE id = :id")
    suspend fun updateMeta(id: Long, agentId: Long?, mode: String, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    fun observeByConversation(conversationId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY id ASC")
    suspend fun getByConversation(conversationId: Long): List<MessageEntity>

    @Insert
    suspend fun insert(entity: MessageEntity): Long

    @Insert
    suspend fun insertAll(entities: List<MessageEntity>)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    @Query("UPDATE messages SET toolCallsJson = :toolCallsJson WHERE id = :id")
    suspend fun updateToolCalls(id: Long, toolCallsJson: String)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun updateStarred(id: Long, starred: Boolean)

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE toolCallId IN (:ids)")
    suspend fun deleteByToolCallIds(ids: List<String>)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("UPDATE messages SET role = :role, content = :content WHERE id = :id")
    suspend fun updateRoleAndContent(id: Long, role: String, content: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :afterId AND id < :beforeId")
    suspend fun deleteRange(conversationId: Long, afterId: Long, beforeId: Long)

    @Query(
        """
        SELECT m.* FROM messages m
        INNER JOIN conversations c ON c.id = m.conversationId
        WHERE m.starred = 1
        ORDER BY m.id DESC
        """,
    )
    fun observeStarred(): Flow<List<MessageEntity>>

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :afterId")
    suspend fun deleteAfter(conversationId: Long, afterId: Long)
}

@Dao
interface RunDao {

    @Insert
    suspend fun insert(entity: RunEntity): Long

    @Query("UPDATE runs SET state = :state, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: Long, state: String, now: Long)

    @Query("SELECT * FROM runs WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun latestForConversation(conversationId: Long): RunEntity?

    @Query(
        """
        SELECT r.id AS id, r.conversationId AS conversationId, c.title AS conversationTitle,
               r.mode AS mode, r.state AS state, r.startedAt AS startedAt, r.updatedAt AS updatedAt
        FROM runs r
        INNER JOIN conversations c ON c.id = r.conversationId
        ORDER BY r.id DESC
        LIMIT :limit
        """,
    )
    fun observeSummaries(limit: Int = 100): Flow<List<RunSummaryRow>>
}

data class RunSummaryRow(
    val id: Long,
    val conversationId: Long,
    val conversationTitle: String,
    val mode: String,
    val state: String,
    val startedAt: Long,
    val updatedAt: Long,
)

@Dao
interface PlanDao {

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanEntity)

    @Query("SELECT * FROM plans WHERE conversationId = :conversationId")
    suspend fun get(conversationId: Long): PlanEntity?

    @Query("SELECT * FROM plans WHERE conversationId = :conversationId")
    fun observe(conversationId: Long): Flow<PlanEntity?>
}

@Dao
interface MemoryDao {

    @Insert
    suspend fun insert(entity: MemoryEntity): Long

    @Query("SELECT * FROM memories WHERE kind = :kind ORDER BY id ASC")
    fun observeByKind(kind: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE kind = :kind ORDER BY id ASC")
    suspend fun getByKind(kind: String): List<MemoryEntity>

    @Query("SELECT COUNT(*) FROM memories WHERE kind = :kind")
    suspend fun countByKind(kind: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM memories WHERE kind = :kind AND content = :content)")
    suspend fun exists(kind: String, content: String): Boolean

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memories WHERE kind = :kind")
    suspend fun deleteByKind(kind: String)
}
