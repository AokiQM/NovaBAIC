package com.verlintas.baic2.core.data.repository

import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.Automation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class AutomationRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    fun observeAll(): Flow<List<Automation>> =
        db.automationDao().observeAll().map { list -> list.map(mapper::automationToModel) }

    suspend fun getAll(): List<Automation> =
        db.automationDao().getAll().map(mapper::automationToModel)

    suspend fun getById(id: Long): Automation? =
        db.automationDao().getById(id)?.let(mapper::automationToModel)

    suspend fun save(automation: Automation): Long {
        val entity = mapper.automationToEntity(automation)
        return if (automation.id == 0L) {
            db.automationDao().insert(entity)
        } else {
            db.automationDao().setEnabled(automation.id, automation.enabled)
            automation.id
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) =
        db.automationDao().setEnabled(id, enabled)

    suspend fun delete(id: Long) = db.automationDao().delete(id)
}
