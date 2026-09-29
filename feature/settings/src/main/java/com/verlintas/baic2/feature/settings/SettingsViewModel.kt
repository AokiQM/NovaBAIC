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
import com.verlintas.baic2.core.data.prefs.AppLocaleStore
import com.verlintas.baic2.core.data.prefs.SettingsRepository
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.storage.AppStorage
import com.verlintas.baic2.core.data.storage.StorageUsage
import com.verlintas.baic2.core.model.AccentColor
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.AppLanguage
import com.verlintas.baic2.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val agents: List<Agent> = emptyList(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.BLUE,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val storage: StorageUsage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
    private val localeStore: AppLocaleStore,
    private val appStorage: AppStorage,
) : ViewModel() {

    private val storage = MutableStateFlow<StorageUsage?>(null)

    init {
        refreshStorage()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        agentRepository.observeAgents(),
        settingsRepository.themeMode,
        settingsRepository.accentColor,
        localeStore.language,
        storage,
    ) { agents, themeMode, accent, language, storageUsage ->
        SettingsUiState(
            agents = agents,
            themeMode = themeMode,
            accent = accent,
            language = language,
            storage = storageUsage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launch { settingsRepository.setAccentColor(accent) }
    }

    fun setLanguage(language: AppLanguage) {
        localeStore.set(language)
    }

    fun setDefaultAgent(id: Long) {
        viewModelScope.launch { agentRepository.setDefault(id) }
    }

    fun deleteAgent(id: Long) {
        viewModelScope.launch { agentRepository.delete(id) }
    }

    fun refreshStorage() {
        viewModelScope.launch { storage.value = appStorage.usage() }
    }

    fun clearAttachments(onDone: () -> Unit) {
        viewModelScope.launch {
            appStorage.clearAttachments()
            refreshStorage()
            onDone()
        }
    }

    fun clearScreenshots(onDone: () -> Unit) {
        viewModelScope.launch {
            appStorage.clearScreenshots()
            refreshStorage()
            onDone()
        }
    }

    fun clearConversations(onDone: () -> Unit) {
        viewModelScope.launch {
            appStorage.clearConversations()
            refreshStorage()
            onDone()
        }
    }
}
