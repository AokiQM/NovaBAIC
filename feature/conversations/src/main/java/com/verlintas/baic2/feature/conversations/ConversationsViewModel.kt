package com.verlintas.baic2.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.Conversation
import com.verlintas.baic2.core.model.ConversationPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ConversationsUiState(
    val loading: Boolean = true,
    val conversations: List<ConversationPreview> = emptyList(),
    val agentName: String? = null,
    val hasAgent: Boolean = false,
)

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
) : ViewModel() {

    val uiState: StateFlow<ConversationsUiState> = combine(
        conversationRepository.observeConversations(),
        agentRepository.observeDefaultAgent(),
    ) { conversations, agent ->
        ConversationsUiState(
            loading = false,
            conversations = conversations,
            agentName = agent?.name,
            hasAgent = agent != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConversationsUiState(loading = true),
    )

    private val _openConversation = MutableSharedFlow<Long>()
    val openConversation: SharedFlow<Long> = _openConversation

    fun createConversation() {
        viewModelScope.launch {
            val id = conversationRepository.create(
                agentId = null,
                mode = AppMode.CHAT,
                title = "",
            )
            _openConversation.emit(id)
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch { conversationRepository.delete(id) }
    }
}
