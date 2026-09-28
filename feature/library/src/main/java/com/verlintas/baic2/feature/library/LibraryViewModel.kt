package com.verlintas.baic2.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.MemoryRepository
import com.verlintas.baic2.core.model.Automation
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.MemoryKind
import com.verlintas.baic2.tools.automation.AutomationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val automations: List<Automation> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val automationManager: AutomationManager,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        automationManager.observeAll(),
        memoryRepository.observe(MemoryKind.MEMORY),
    ) { automations, memories ->
        LibraryUiState(automations = automations, memories = memories, loading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setAutomationEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { automationManager.setEnabled(id, enabled) }
    }

    fun deleteAutomation(id: Long) {
        viewModelScope.launch { automationManager.delete(id) }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { memoryRepository.delete(id) }
    }
}
