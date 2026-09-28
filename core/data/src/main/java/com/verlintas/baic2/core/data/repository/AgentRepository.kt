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

package com.verlintas.baic2.core.data.repository

import androidx.room.withTransaction
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.data.security.SecretCipher
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.ProviderConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ApiKeyUnavailableException : Exception("Stored API key could not be decrypted")

@Singleton
class AgentRepository @Inject constructor(
    private val db: Baic2Database,
    private val cipher: SecretCipher,
    private val mapper: ChatMapper,
) {

    fun observeAgents(): Flow<List<Agent>> =
        db.agentDao().observeAll().map { list -> list.map(mapper::agentToModel) }

    fun observeDefaultAgent(): Flow<Agent?> =
        db.agentDao().observeDefault().map { entity -> entity?.let(mapper::agentToModel) }

    suspend fun getAgent(id: Long): Agent? = db.agentDao().getById(id)?.let(mapper::agentToModel)

    suspend fun getDefaultAgent(): Agent? = db.agentDao().getDefault()?.let(mapper::agentToModel)

    suspend fun save(agent: Agent, plaintextApiKey: String): Long = db.withTransaction {
        val entity = mapper.agentToEntity(
            agent = agent,
            encryptedApiKey = cipher.encrypt(plaintextApiKey),
            createdAt = System.currentTimeMillis(),
        )
        val id = db.agentDao().insert(entity)
        if (entity.isDefault || db.agentDao().count() == 1) {
            db.agentDao().clearDefault()
            db.agentDao().markDefault(id)
        }
        id
    }

    suspend fun setDefault(id: Long) = db.withTransaction {
        db.agentDao().clearDefault()
        db.agentDao().markDefault(id)
    }

    suspend fun delete(id: Long) = db.withTransaction {
        db.agentDao().delete(id)
    }

    /** Resolves the runtime config for a conversation, decrypting the key. */
    suspend fun resolveConfig(agentId: Long?): ProviderConfig? {
        val entity = agentId?.let { db.agentDao().getById(it) } ?: db.agentDao().getDefault()
            ?: return null
        val agent = mapper.agentToModel(entity)
        val apiKey = cipher.decrypt(entity.encryptedApiKey).getOrElse {
            throw ApiKeyUnavailableException()
        }
        return ProviderConfig(
            provider = agent.provider,
            baseUrl = agent.baseUrl,
            apiKey = apiKey,
            model = agent.model,
            temperature = agent.temperature,
            maxTokens = agent.maxTokens,
            reasoning = agent.reasoning,
        )
    }
}
