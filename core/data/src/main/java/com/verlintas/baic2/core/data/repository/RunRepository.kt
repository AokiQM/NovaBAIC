package com.verlintas.baic2.core.data.repository

import androidx.room.withTransaction
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.db.RunEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.RunSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class RunRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeSummaries(): Flow<List<RunSummary>> =
        db.runDao().observeSummaries().map { rows -> rows.map(mapper::runSummaryToModel) }

    suspend fun start(conversationId: Long, mode: AppMode): Long {
        val now = System.currentTimeMillis()
        return db.runDao().insert(
            RunEntity(
                conversationId = conversationId,
                mode = mode.name,
                state = RunState.RUNNING.name,
                startedAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun finish(runId: Long, state: RunState) = db.withTransaction {
        db.runDao().updateState(runId, state.name, System.currentTimeMillis())
    }
}
