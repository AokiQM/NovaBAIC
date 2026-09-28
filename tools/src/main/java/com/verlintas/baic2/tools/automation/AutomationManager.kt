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
