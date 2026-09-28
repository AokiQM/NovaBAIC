package com.verlintas.baic2.core.data.repository

import androidx.room.withTransaction
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.db.RunEntity
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.RunState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RunRepository @Inject constructor(
    private val db: Baic2Database,
) {

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
