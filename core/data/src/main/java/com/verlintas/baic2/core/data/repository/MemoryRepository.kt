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
import com.verlintas.baic2.core.data.db.MemoryEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.MemoryKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemoryRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observe(kind: MemoryKind): Flow<List<Memory>> =
        db.memoryDao().observeByKind(kind.name).map { list -> list.map(mapper::memoryToModel) }

    suspend fun list(kind: MemoryKind): List<Memory> =
        db.memoryDao().getByKind(kind.name).map(mapper::memoryToModel)

    /** Returns false when the exact same fact already exists. */
    suspend fun add(kind: MemoryKind, content: String, conversationId: Long? = null): Boolean {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return false
        if (db.memoryDao().exists(kind.name, trimmed)) return false
        db.memoryDao().insert(
            MemoryEntity(
                kind = kind.name,
                content = trimmed,
                conversationId = conversationId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    suspend fun delete(id: Long) = db.memoryDao().delete(id)

    suspend fun clear(kind: MemoryKind) = db.memoryDao().deleteByKind(kind.name)

    suspend fun count(kind: MemoryKind): Int = db.memoryDao().countByKind(kind.name)
}
