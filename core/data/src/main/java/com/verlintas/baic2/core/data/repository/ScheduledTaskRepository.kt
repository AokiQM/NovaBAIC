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
import com.verlintas.baic2.core.model.ScheduledTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ScheduledTaskRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeAll(): Flow<List<ScheduledTask>> =
        db.scheduledTaskDao().observeAll().map { list -> list.map(mapper::scheduledTaskToModel) }

    suspend fun getById(id: Long): ScheduledTask? =
        db.scheduledTaskDao().getById(id)?.let(mapper::scheduledTaskToModel)

    suspend fun getEnabled(): List<ScheduledTask> =
        db.scheduledTaskDao().getEnabled().map(mapper::scheduledTaskToModel)

    suspend fun save(task: ScheduledTask): Long {
        val now = System.currentTimeMillis()
        val entity = mapper.scheduledTaskToEntity(
            task.copy(createdAt = if (task.createdAt == 0L) now else task.createdAt),
        )
        return if (task.id == 0L) {
            db.scheduledTaskDao().insert(entity)
        } else {
            db.scheduledTaskDao().update(entity)
            task.id
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = db.scheduledTaskDao().setEnabled(id, enabled)

    suspend fun updateAfterRun(
        id: Long,
        conversationId: Long?,
        lastRunAt: Long,
        nextRunAt: Long,
        lastResult: String?,
    ) = db.scheduledTaskDao().updateAfterRun(id, conversationId, lastRunAt, nextRunAt, lastResult)

    suspend fun delete(id: Long) = db.scheduledTaskDao().delete(id)
}
