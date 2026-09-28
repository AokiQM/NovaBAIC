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
