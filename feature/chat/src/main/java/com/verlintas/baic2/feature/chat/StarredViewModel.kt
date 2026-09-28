package com.verlintas.baic2.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class StarredViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
) : ViewModel() {

    val messages: StateFlow<List<ChatMessage>> =
        conversationRepository.observeStarredMessages().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun unstar(messageId: Long) {
        viewModelScope.launch { conversationRepository.setStarred(messageId, false) }
    }
}
