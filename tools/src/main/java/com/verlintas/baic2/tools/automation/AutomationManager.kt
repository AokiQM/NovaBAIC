package com.verlintas.baic2.tools.automation

import com.verlintas.baic2.core.data.repository.AutomationRepository
import com.verlintas.baic2.core.model.Automation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** UI-facing facade over automation storage + alarm scheduling. */
@Singleton
class AutomationManager @Inject constructor(
    private val repository: AutomationRepository,
    private val scheduler: AutomationScheduler,
) {

    fun observeAll(): Flow<List<Automation>> = repository.observeAll()

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        repository.setEnabled(id, enabled)
        val automation = repository.getById(id) ?: return
        if (enabled) scheduler.schedule(automation) else scheduler.cancel(id)
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        repository.delete(id)
    }
}
