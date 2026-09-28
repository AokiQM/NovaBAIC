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
