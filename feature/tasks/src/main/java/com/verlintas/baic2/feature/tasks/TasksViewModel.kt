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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.data.repository.PlanRepository
import com.verlintas.baic2.core.data.repository.RunRepository
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.Plan
import com.verlintas.baic2.core.model.Run
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.RunSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TaskFilter { ALL, RUNNING, COMPLETED, FAILED }

data class TasksUiState(
    val runs: List<RunSummary> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val totalCount: Int = 0,
    val runningCount: Int = 0,
)

data class RunDetailState(
    val run: Run,
    val conversationTitle: String,
    val plan: Plan?,
    val messages: List<ChatMessage>,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val runRepository: RunRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.ALL)

    val uiState: StateFlow<TasksUiState> = combine(
        runRepository.observeSummaries(),
        filter,
    ) { runs, selected ->
        TasksUiState(
            runs = runs.filter { run ->
                when (selected) {
                    TaskFilter.ALL -> true
                    TaskFilter.RUNNING -> run.state == RunState.RUNNING
                    TaskFilter.COMPLETED -> run.state == RunState.COMPLETED
                    TaskFilter.FAILED -> run.state == RunState.FAILED || run.state == RunState.CANCELLED
                }
            },
            filter = selected,
            totalCount = runs.size,
            runningCount = runs.count { it.state == RunState.RUNNING },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TasksUiState(),
    )

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun deleteRun(runId: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            runRepository.delete(runId)
            onDone()
        }
    }
}

@HiltViewModel
class RunDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val runRepository: RunRepository,
    planRepository: PlanRepository,
    conversationRepository: ConversationRepository,
) : ViewModel() {

    val runId: Long = savedStateHandle.get<Long>("runId") ?: 0L

    val detail: StateFlow<RunDetailState?> = runRepository.observeRun(runId)
        .flatMapLatest { run ->
            if (run == null) {
                flowOf(null)
            } else {
                combine(
                    conversationRepository.observeConversation(run.conversationId),
                    planRepository.observePlan(run.conversationId),
                    conversationRepository.observeMessages(run.conversationId),
                ) { conversation, plan, messages ->
                    RunDetailState(
                        run = run,
                        conversationTitle = conversation?.title.orEmpty(),
                        plan = plan,
                        messages = messagesForRun(run, messages),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    fun deleteRun(onDone: () -> Unit) {
        viewModelScope.launch {
            runRepository.delete(runId)
            onDone()
        }
    }
}

/**
 * Reconstructs the messages that belong to a run from timestamps: the prompt is
 * persisted right after the run starts, and the run's `updatedAt` is stamped
 * again when it finishes.
 */
private fun messagesForRun(run: Run, messages: List<ChatMessage>): List<ChatMessage> {
    val from = run.startedAt - 2_000
    val to = if (run.state == RunState.RUNNING) Long.MAX_VALUE else run.updatedAt + 2_000
    return messages.filter { message ->
        message.role != ChatRole.SYSTEM && message.createdAt in from..to
    }
}
