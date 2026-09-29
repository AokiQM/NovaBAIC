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

package com.verlintas.baic2.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.ScheduledTaskControl
import com.verlintas.baic2.core.data.repository.ScheduledTaskRepository
import com.verlintas.baic2.core.model.ScheduledTask
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduledTasksViewModel @Inject constructor(
    private val repository: ScheduledTaskRepository,
    private val control: ScheduledTaskControl,
) : ViewModel() {

    val tasks: StateFlow<List<ScheduledTask>> = repository.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun save(task: ScheduledTask, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val id = repository.save(task)
            repository.getById(id)?.let(control::schedule)
            onSaved()
        }
    }

    fun setEnabled(task: ScheduledTask, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(task.id, enabled)
            if (enabled) control.schedule(task.copy(enabled = true)) else control.cancel(task.id)
        }
    }

    fun delete(task: ScheduledTask) {
        viewModelScope.launch {
            repository.delete(task.id)
            control.cancel(task.id)
        }
    }

    fun runNow(task: ScheduledTask) {
        control.runNow(task.id)
    }
}
