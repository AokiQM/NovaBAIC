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

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val provider: String,
    val baseUrl: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int?,
    val reasoning: Boolean,
    val systemPrompt: String,
    val encryptedApiKey: String,
    val isDefault: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val agentId: Long?,
    val mode: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["conversationId"]), Index(value = ["starred"])],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val role: String,
    val content: String,
    val thinking: String?,
    val toolCallsJson: String,
    val attachmentsJson: String = "[]",
    val toolCallId: String?,
    val toolName: String?,
    val model: String?,
    val createdAt: Long,
    val starred: Boolean = false,
)

@Entity(
    tableName = "memories",
    indices = [Index(value = ["kind"])],
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val content: String,
    val conversationId: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "runs",
    indices = [Index(value = ["conversationId"])],
)
data class RunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: Long,
    val mode: String,
    val state: String,
    val startedAt: Long,
    val updatedAt: Long,
)

data class ConversationSummary(
    @Embedded val conversation: ConversationEntity,
    val lastMessage: String?,
)

@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey val conversationId: Long,
    val stepsJson: String,
    val updatedAt: Long,
)
