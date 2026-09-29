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

    @Query("SELECT COUNT(*) FROM conversations")
    suspend fun count(): Int

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

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId " +
            "AND id >= :afterId AND id < :beforeId ORDER BY id ASC",
    )
    suspend fun getRange(conversationId: Long, afterId: Long, beforeId: Long): List<MessageEntity>

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

    @Query("UPDATE messages SET usageInput = NULL, usageOutput = NULL WHERE conversationId = :conversationId")
    suspend fun clearUsage(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteAllForConversation(conversationId: Long)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND id > :afterId")
    suspend fun deleteAfter(conversationId: Long, afterId: Long)

    @Query("SELECT COUNT(*) FROM messages")
    suspend fun countAll(): Int
}

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY id ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: Long): ScheduledTaskEntity?

    @Insert
    suspend fun insert(entity: ScheduledTaskEntity): Long

    @Update
    suspend fun update(entity: ScheduledTaskEntity)

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query(
        "UPDATE scheduled_tasks SET conversationId = :conversationId, lastRunAt = :lastRunAt, " +
            "nextRunAt = :nextRunAt, lastResult = :lastResult WHERE id = :id",
    )
    suspend fun updateAfterRun(
        id: Long,
        conversationId: Long?,
        lastRunAt: Long,
        nextRunAt: Long,
        lastResult: String?,
    )

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SnapshotDao {

    @Insert
    suspend fun insert(entity: SnapshotEntity): Long

    @Query("SELECT * FROM message_snapshots WHERE conversationId = :conversationId ORDER BY id DESC")
    fun observeForConversation(conversationId: Long): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM message_snapshots WHERE id = :id")
    suspend fun getById(id: Long): SnapshotEntity?

    @Query("DELETE FROM message_snapshots WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RunDao {

    @Insert
    suspend fun insert(entity: RunEntity): Long

    @Query(
        "UPDATE runs SET state = :state, roundsUsed = :rounds, toolCallsUsed = :toolCalls, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun finish(id: Long, state: String, rounds: Int, toolCalls: Int, now: Long)

    @Query("SELECT * FROM runs WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 1")
    suspend fun latestForConversation(conversationId: Long): RunEntity?

    @Query("SELECT * FROM runs WHERE id = :id")
    fun observeById(id: Long): Flow<RunEntity?>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun getById(id: Long): RunEntity?

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE runs SET state = 'CANCELLED', updatedAt = :now WHERE state = 'RUNNING'")
    suspend fun cancelStale(now: Long)

    @Query("DELETE FROM runs WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query(
        """
        SELECT r.id AS id, r.conversationId AS conversationId, c.title AS conversationTitle,
               r.mode AS mode, r.state AS state, r.startedAt AS startedAt, r.updatedAt AS updatedAt
        FROM runs r
        INNER JOIN conversations c ON c.id = r.conversationId
        WHERE r.mode IN ('ACT', 'MAX')
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
interface McpServerDao {

    @Insert
    suspend fun insert(entity: McpServerEntity): Long

    @Query("SELECT * FROM mcp_servers ORDER BY id ASC")
    fun observeAll(): Flow<List<McpServerEntity>>

    @Query("SELECT * FROM mcp_servers ORDER BY id ASC")
    suspend fun getAll(): List<McpServerEntity>

    @Query("UPDATE mcp_servers SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AutomationDao {

    @Insert
    suspend fun insert(entity: AutomationEntity): Long

    @Query("SELECT * FROM automations ORDER BY id DESC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Query("SELECT * FROM automations ORDER BY id DESC")
    suspend fun getAll(): List<AutomationEntity>

    @Query("SELECT * FROM automations WHERE id = :id")
    suspend fun getById(id: Long): AutomationEntity?

    @Query("UPDATE automations SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun delete(id: Long)
}

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
