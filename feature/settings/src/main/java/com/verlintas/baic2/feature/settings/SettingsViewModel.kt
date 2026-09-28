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

package com.verlintas.baic2.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.prefs.SettingsRepository
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.ThemeMode
import com.verlintas.baic2.device.api.AccessibilityBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val agents: List<Agent> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accessibilityEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
    private val accessibilityBridge: AccessibilityBridge,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        agentRepository.observeAgents(),
        settingsRepository.themeMode,
        accessibilityBridge.connected,
    ) { agents, themeMode, accessibility ->
        SettingsUiState(
            loading = false,
            agents = agents,
            themeMode = themeMode,
            accessibilityEnabled = accessibility,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setDefaultAgent(id: Long) {
        viewModelScope.launch { agentRepository.setDefault(id) }
    }

    fun deleteAgent(id: Long) {
        viewModelScope.launch { agentRepository.delete(id) }
    }
}
