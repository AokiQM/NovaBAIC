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

import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.McpServer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class McpServerRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeAll(): Flow<List<McpServer>> =
        db.mcpServerDao().observeAll().map { list -> list.map(mapper::mcpServerToModel) }

    suspend fun getAll(): List<McpServer> =
        db.mcpServerDao().getAll().map(mapper::mcpServerToModel)

    suspend fun add(name: String, url: String): Long =
        db.mcpServerDao().insert(
            mapper.mcpServerToEntity(
                McpServer(
                    name = name,
                    url = url,
                    createdAt = System.currentTimeMillis(),
                ),
            ),
        )

    suspend fun setEnabled(id: Long, enabled: Boolean) =
        db.mcpServerDao().setEnabled(id, enabled)

    suspend fun delete(id: Long) = db.mcpServerDao().delete(id)
}
