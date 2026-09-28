package com.verlintas.baic2.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.McpServerRepository
import com.verlintas.baic2.core.data.repository.MemoryRepository
import com.verlintas.baic2.core.model.Automation
import com.verlintas.baic2.core.model.Memory
import com.verlintas.baic2.core.model.McpServer
import com.verlintas.baic2.core.model.MemoryKind
import com.verlintas.baic2.core.model.Skill
import com.verlintas.baic2.tools.automation.AutomationManager
import com.verlintas.baic2.mcp.McpManager
import com.verlintas.baic2.mcp.McpServerStatus
import com.verlintas.baic2.tools.skills.SkillRepository
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
    val skills: List<Skill> = emptyList(),
    val mcpServers: List<McpServer> = emptyList(),
    val mcpStatus: Map<Long, McpServerStatus> = emptyMap(),
    val loading: Boolean = true,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val automationManager: AutomationManager,
    private val memoryRepository: MemoryRepository,
    private val skillRepository: SkillRepository,
    private val mcpServerRepository: McpServerRepository,
    private val mcpManager: McpManager,
) : ViewModel() {

    fun addMcpServer(name: String, url: String) {
        if (name.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            mcpServerRepository.add(name.trim(), url.trim())
            mcpManager.refresh()
        }
    }

    fun setMcpEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            mcpServerRepository.setEnabled(id, enabled)
            mcpManager.refresh()
        }
    }

    fun deleteMcpServer(id: Long) {
        viewModelScope.launch {
            mcpServerRepository.delete(id)
            mcpManager.refresh()
        }
    }

    private val skills = kotlinx.coroutines.flow.MutableStateFlow<List<Skill>>(emptyList())

    init {
        refreshSkills()
    }

    private fun refreshSkills() {
        viewModelScope.launch { skills.value = skillRepository.list() }
    }

    fun importSkill(uri: android.net.Uri, displayName: String?) {
        viewModelScope.launch {
            skillRepository.import(uri, displayName).onSuccess { refreshSkills() }
        }
    }

    fun deleteSkill(id: String) {
        viewModelScope.launch {
            skillRepository.delete(id)
            refreshSkills()
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        automationManager.observeAll(),
        memoryRepository.observe(MemoryKind.MEMORY),
        skills,
        mcpServerRepository.observeAll(),
        mcpManager.status,
    ) { automations, memories, skillList, mcpServers, mcpStatus ->
        LibraryUiState(
            automations = automations,
            memories = memories,
            skills = skillList,
            mcpServers = mcpServers,
            mcpStatus = mcpStatus,
            loading = false,
        )
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
