package com.verlintas.baic2.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.repository.ApiKeyUnavailableException
import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.data.repository.RunRepository
import com.verlintas.baic2.core.engine.AgentEvent
import com.verlintas.baic2.core.engine.AgentFailure
import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolCallStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StreamingState(
    val text: String = "",
    val thinking: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
)

data class ChatUiState(
    val title: String = "",
    val mode: AppMode = AppMode.CHAT,
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val streamingThinking: String = "",
    val liveToolCalls: List<ToolCall> = emptyList(),
    val isRunning: Boolean = false,
    val error: ChatError? = null,
)

data class ChatError(
    val kind: Kind,
    val detail: String? = null,
) {
    enum class Kind {
        PROVIDER,
        BUDGET,
        NO_AGENT,
        API_KEY,
        UNSUPPORTED,
        INTERNAL,
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val runRepository: RunRepository,
    private val agentLoop: AgentLoop,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: Long = checkNotNull(savedStateHandle[ARG_CONVERSATION_ID])

    private val streaming = MutableStateFlow(StreamingState())
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<ChatError?>(null)

    val uiState: StateFlow<ChatUiState> = combine(
        conversationRepository.observeConversation(conversationId),
        conversationRepository.observeMessages(conversationId),
        streaming,
        running,
        error,
    ) { conversation, messages, stream, isRunning, currentError ->
        ChatUiState(
            title = conversation?.title.orEmpty(),
            mode = conversation?.mode ?: AppMode.CHAT,
            messages = messages,
            streamingText = stream.text,
            streamingThinking = stream.thinking,
            liveToolCalls = stream.toolCalls,
            isRunning = isRunning,
            error = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    private var runJob: Job? = null

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || running.value) return
        runJob = viewModelScope.launch { executeTurn(trimmed) }
    }

    fun stop() {
        runJob?.cancel()
    }

    /** Drops everything after the last user message and runs that turn again. */
    fun retryLast() {
        if (running.value) return
        runJob = viewModelScope.launch {
            val messages = conversationRepository.getMessages(conversationId)
            val lastUser = messages.lastOrNull { it.role == ChatRole.USER } ?: return@launch
            conversationRepository.deleteMessagesAfter(conversationId, lastUser.id)
            executeTurn(lastUser.content, appendUserMessage = false)
        }
    }

    fun dismissError() {
        error.value = null
    }

    fun setMode(mode: AppMode) {
        viewModelScope.launch {
            val conversation = conversationRepository.get(conversationId) ?: return@launch
            conversationRepository.updateMeta(conversationId, conversation.agentId, mode)
        }
    }

    private suspend fun executeTurn(text: String, appendUserMessage: Boolean = true) {
        val conversation = conversationRepository.get(conversationId) ?: return
        val agent = conversation.agentId?.let { agentRepository.getAgent(it) }
            ?: agentRepository.getDefaultAgent()

        val config = try {
            agentRepository.resolveConfig(conversation.agentId)
        } catch (e: ApiKeyUnavailableException) {
            error.value = ChatError(ChatError.Kind.API_KEY)
            return
        }
        if (config == null) {
            error.value = ChatError(ChatError.Kind.NO_AGENT)
            return
        }

        if (appendUserMessage) {
            val now = System.currentTimeMillis()
            conversationRepository.append(
                ChatMessage(
                    conversationId = conversationId,
                    role = ChatRole.USER,
                    content = text,
                    createdAt = now,
                ),
            )
            if (conversation.title.isBlank()) {
                conversationRepository.updateTitle(conversationId, text.take(TITLE_MAX_CHARS))
            }
        }

        val history = conversationRepository.getMessages(conversationId)
        val runId = runRepository.start(conversationId, conversation.mode)

        running.value = true
        error.value = null
        var assistantMessageId: Long? = null
        var pendingCalls: List<ToolCall> = emptyList()

        try {
            agentLoop.run(
                config = config,
                mode = conversation.mode,
                customSystemPrompt = agent?.systemPrompt.orEmpty(),
                history = history,
            ).collect { event ->
                when (event) {
                    is AgentEvent.RoundStarted -> {
                        if (event.round > 1) streaming.value = StreamingState()
                        pendingCalls = emptyList()
                    }

                    is AgentEvent.TextDelta ->
                        streaming.update { it.copy(text = it.text + event.text) }

                    is AgentEvent.ThinkingDelta ->
                        streaming.update { it.copy(thinking = it.thinking + event.text) }

                    is AgentEvent.AssistantMessage -> {
                        val storedId = conversationRepository.append(
                            event.message.copy(
                                conversationId = conversationId,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                        assistantMessageId = storedId
                        pendingCalls = event.message.toolCalls
                        streaming.value = StreamingState()
                    }

                    is AgentEvent.ToolCallStarted -> {
                        pendingCalls = pendingCalls.map { call ->
                            if (call.id == event.call.id) {
                                event.call.copy(status = ToolCallStatus.RUNNING)
                            } else {
                                call
                            }
                        }
                        assistantMessageId?.let { id ->
                            conversationRepository.updateToolCalls(id, pendingCalls)
                        }
                        streaming.update { it.copy(toolCalls = pendingCalls) }
                    }

                    is AgentEvent.ToolCallFinished -> {
                        pendingCalls = pendingCalls.map { call ->
                            if (call.id == event.call.id) event.call else call
                        }
                        assistantMessageId?.let { id ->
                            conversationRepository.updateToolCalls(id, pendingCalls)
                        }
                        streaming.update { it.copy(toolCalls = pendingCalls) }
                    }

                    is AgentEvent.Usage -> Unit

                    AgentEvent.Completed -> runRepository.finish(runId, RunState.COMPLETED)

                    is AgentEvent.Failed -> {
                        error.value = event.error.toChatError()
                        runRepository.finish(runId, RunState.FAILED)
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                val partial = streaming.value.text
                if (partial.isNotBlank()) {
                    conversationRepository.append(
                        ChatMessage(
                            conversationId = conversationId,
                            role = ChatRole.ASSISTANT,
                            content = partial,
                            model = config.model,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                rejectPendingToolCalls()
                runRepository.finish(runId, RunState.CANCELLED)
            }
            throw e
        } catch (e: Exception) {
            error.value = ChatError(ChatError.Kind.INTERNAL, e.message)
            runRepository.finish(runId, RunState.FAILED)
        } finally {
            running.value = false
            streaming.value = StreamingState()
        }
    }

    /**
     * A stop between rounds must not leave assistant tool calls without
     * results — the next request would violate the provider protocol.
     */
    private suspend fun rejectPendingToolCalls() {
        val messages = conversationRepository.getMessages(conversationId)
        val answeredIds = messages.filter { it.role == ChatRole.TOOL }
            .mapNotNull { it.toolCallId }
            .toSet()

        messages.filter { it.role == ChatRole.ASSISTANT && it.toolCalls.isNotEmpty() }
            .forEach { message ->
                val pending = message.toolCalls.filter { call ->
                    call.status == ToolCallStatus.PENDING && call.id !in answeredIds
                }
                if (pending.isNotEmpty()) {
                    pending.forEach { call ->
                        conversationRepository.append(
                            ChatMessage(
                                conversationId = conversationId,
                                role = ChatRole.TOOL,
                                content = "ERROR: cancelled by user",
                                toolCallId = call.id,
                                toolName = call.name,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    val pendingIds = pending.map { it.id }.toSet()
                    val updated = message.toolCalls.map { call ->
                        if (call.id in pendingIds) {
                            call.copy(status = ToolCallStatus.REJECTED, result = "ERROR: cancelled by user")
                        } else {
                            call
                        }
                    }
                    conversationRepository.updateToolCalls(message.id, updated)
                }
            }
    }

    private fun AgentFailure.toChatError(): ChatError = when (kind) {
        AgentFailure.Kind.PROVIDER -> ChatError(ChatError.Kind.PROVIDER, message)
        AgentFailure.Kind.BUDGET -> ChatError(ChatError.Kind.BUDGET, message)
        AgentFailure.Kind.UNSUPPORTED_PROVIDER -> ChatError(ChatError.Kind.UNSUPPORTED, message)
        AgentFailure.Kind.INTERNAL -> ChatError(ChatError.Kind.INTERNAL, message)
    }

    companion object {
        const val ARG_CONVERSATION_ID = "conversationId"
        private const val TITLE_MAX_CHARS = 24
    }
}
