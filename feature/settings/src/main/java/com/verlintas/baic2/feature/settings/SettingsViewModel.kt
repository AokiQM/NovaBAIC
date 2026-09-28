package com.verlintas.baic2.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.prefs.SettingsRepository
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.ThemeMode
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
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        agentRepository.observeAgents(),
        settingsRepository.themeMode,
    ) { agents, themeMode ->
        SettingsUiState(loading = false, agents = agents, themeMode = themeMode)
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
