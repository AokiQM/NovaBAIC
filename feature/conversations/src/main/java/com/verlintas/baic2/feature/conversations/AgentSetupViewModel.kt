package com.verlintas.baic2.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.network.provider.ProviderException
import com.verlintas.baic2.core.network.provider.ProviderFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Preset endpoints; labels live in the UI layer. */
enum class AgentPreset(
    val id: String,
    val provider: ProviderId,
    val baseUrl: String,
    val model: String,
    val defaultName: String,
) {
    DEEPSEEK("deepseek", ProviderId.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "deepseek-chat", "DeepSeek"),
    OPENAI("openai", ProviderId.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o-mini", "OpenAI"),
    SILICONFLOW(
        "siliconflow",
        ProviderId.OPENAI_COMPATIBLE,
        "https://api.siliconflow.cn/v1",
        "deepseek-ai/DeepSeek-V3",
        "SiliconFlow",
    ),
    MOONSHOT("moonshot", ProviderId.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1", "moonshot-v1-8k", "Moonshot"),
    QWEN(
        "qwen",
        ProviderId.OPENAI_COMPATIBLE,
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "qwen-plus",
        "Qwen",
    ),
    CLAUDE("claude", ProviderId.ANTHROPIC, "https://api.anthropic.com", "claude-sonnet-4-5", "Claude"),
    GEMINI("gemini", ProviderId.GEMINI, "https://generativelanguage.googleapis.com", "gemini-2.5-flash", "Gemini"),
    CUSTOM("custom", ProviderId.OPENAI_COMPATIBLE, "", "", ""),
}

data class AgentSetupUiState(
    val preset: AgentPreset = AgentPreset.DEEPSEEK,
    val name: String = AgentPreset.DEEPSEEK.defaultName,
    val baseUrl: String = AgentPreset.DEEPSEEK.baseUrl,
    val apiKey: String = "",
    val model: String = AgentPreset.DEEPSEEK.model,
    val models: List<String> = emptyList(),
    val fetchingModels: Boolean = false,
    val saving: Boolean = false,
    val error: SetupError? = null,
    val saved: Boolean = false,
)

sealed interface SetupError {
    data object MissingKey : SetupError

    data object MissingUrl : SetupError

    data object MissingModel : SetupError

    data object KeyStore : SetupError

    data class Request(val kind: ProviderError.Kind, val detail: String) : SetupError
}

@HiltViewModel
class AgentSetupViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val providerFactory: ProviderFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentSetupUiState())
    val uiState: StateFlow<AgentSetupUiState> = _uiState.asStateFlow()

    fun selectPreset(preset: AgentPreset) {
        _uiState.update { current ->
            current.copy(
                preset = preset,
                name = preset.defaultName.ifBlank { current.name },
                baseUrl = preset.baseUrl.ifBlank { current.baseUrl },
                model = preset.model.ifBlank { current.model },
                models = emptyList(),
                error = null,
            )
        }
    }

    fun updateName(value: String) = _uiState.update { it.copy(name = value, error = null) }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value, error = null) }

    fun updateApiKey(value: String) = _uiState.update { it.copy(apiKey = value, error = null) }

    fun updateModel(value: String) = _uiState.update { it.copy(model = value, error = null) }

    fun fetchModels() {
        val current = _uiState.value
        if (current.fetchingModels) return
        if (current.baseUrl.isBlank() || current.apiKey.isBlank() || current.model.isBlank()) {
            _uiState.update { it.copy(error = SetupError.MissingKey) }
            return
        }
        _uiState.update { it.copy(fetchingModels = true, error = null) }
        viewModelScope.launch {
            try {
                val models = providerFactory.create(current.preset.provider)
                    .listModels(current.toConfig())
                    .sorted()
                _uiState.update {
                    it.copy(fetchingModels = false, models = models, error = null)
                }
            } catch (e: ProviderException) {
                _uiState.update {
                    it.copy(
                        fetchingModels = false,
                        error = SetupError.Request(e.error.kind, e.error.message),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        fetchingModels = false,
                        error = SetupError.Request(ProviderError.Kind.UNKNOWN, e.message.orEmpty()),
                    )
                }
            }
        }
    }

    fun save() {
        val current = _uiState.value
        val error = when {
            current.baseUrl.isBlank() -> SetupError.MissingUrl
            current.apiKey.isBlank() -> SetupError.MissingKey
            current.model.isBlank() -> SetupError.MissingModel
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                agentRepository.save(
                    agent = Agent(
                        name = current.name.ifBlank { current.model },
                        provider = current.preset.provider,
                        baseUrl = current.baseUrl.trim().trimEnd('/'),
                        model = current.model.trim(),
                    ),
                    plaintextApiKey = current.apiKey.trim(),
                )
                _uiState.update { it.copy(saving = false, saved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(saving = false, error = SetupError.KeyStore) }
            }
        }
    }

    private fun AgentSetupUiState.toConfig() = ProviderConfig(
        provider = preset.provider,
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiKey = apiKey.trim(),
        model = model.trim(),
    )
}
