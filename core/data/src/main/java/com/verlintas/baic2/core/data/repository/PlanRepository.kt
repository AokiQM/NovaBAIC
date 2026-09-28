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
import com.verlintas.baic2.core.data.db.PlanEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.Plan
import com.verlintas.baic2.core.model.PlanStep
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class PlanRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
    private val json: Json,
) {

    fun observePlan(conversationId: Long): Flow<Plan?> =
        db.planDao().observe(conversationId).map { entity -> entity?.let(mapper::planToModel) }

    suspend fun getPlan(conversationId: Long): Plan? =
        db.planDao().get(conversationId)?.let(mapper::planToModel)

    suspend fun savePlan(conversationId: Long, steps: List<PlanStep>) {
        db.planDao().upsert(
            PlanEntity(
                conversationId = conversationId,
                stepsJson = json.encodeToString(steps),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
