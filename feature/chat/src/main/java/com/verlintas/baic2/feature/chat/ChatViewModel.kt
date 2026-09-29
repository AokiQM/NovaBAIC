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

package com.verlintas.baic2.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.verlintas.baic2.core.data.attachment.AttachmentProcessor
import com.verlintas.baic2.core.data.repository.AgentRepository
import com.verlintas.baic2.core.data.repository.ApiKeyUnavailableException
import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.data.repository.MemoryRepository
import com.verlintas.baic2.core.data.repository.PlanRepository
import com.verlintas.baic2.core.data.repository.RunRepository
import com.verlintas.baic2.core.engine.AgentEvent
import com.verlintas.baic2.core.engine.AgentFailure
import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.AuxiliaryTasks
import com.verlintas.baic2.core.engine.ConfirmationQueue
import com.verlintas.baic2.core.engine.ToolCatalog
import com.verlintas.baic2.core.engine.renderSystemPrompt
import com.verlintas.baic2.core.model.Agent
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.Attachment
import com.verlintas.baic2.core.model.AttachmentKind
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.MemoryKind
import com.verlintas.baic2.core.model.ModelCatalog
import com.verlintas.baic2.core.model.ModelContextWindows
import com.verlintas.baic2.core.model.Plan
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.RunState
import com.verlintas.baic2.core.model.TokenEstimator
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolCallStatus
import com.verlintas.baic2.device.api.RunNotifier
import com.verlintas.baic2.device.api.ScreenshotProvider
import com.verlintas.baic2.device.api.SpeechOutput
import com.verlintas.baic2.tools.skills.SkillRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

data class StreamingState(
    val text: String = "",
    val thinking: String = "",
)

data class ChatUiState(
    val title: String = "",
    val mode: AppMode = AppMode.CHAT,
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val streamingThinking: String = "",
    val isRunning: Boolean = false,
    val error: ChatError? = null,
    val contextUsedTokens: Long? = null,
    val contextWindowTokens: Long? = null,
    val contextEstimated: Boolean = false,
    val auxBusy: Boolean = false,
    val pendingAttachments: List<Attachment> = emptyList(),
    val plan: Plan? = null,
    val confirmRequest: ToolCall? = null,
)

enum class AttachmentError {
    TOO_LARGE,
    UNSUPPORTED,
    READ_FAILED,
}

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
        SCREEN_CAPTURE,
    }
}

data class ExportLabels(
    val you: String,
    val assistant: String,
    val toolCall: String,
    val thinking: String,
    val emptyConversation: String,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val agentRepository: AgentRepository,
    private val runRepository: RunRepository,
    private val memoryRepository: MemoryRepository,
    private val agentLoop: AgentLoop,
    private val toolCatalog: ToolCatalog,
    private val auxiliaryTasks: AuxiliaryTasks,
    private val attachmentProcessor: AttachmentProcessor,
    private val speechOutput: SpeechOutput,
    private val screenshotProvider: ScreenshotProvider,
    private val runNotifier: RunNotifier,
    private val confirmationQueue: ConfirmationQueue,
    private val planRepository: PlanRepository,
    private val skillRepository: SkillRepository,
    private val json: Json,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: Long = checkNotNull(savedStateHandle[ARG_CONVERSATION_ID])

    private val streaming = MutableStateFlow(StreamingState())
    private val running = MutableStateFlow(false)
    private val error = MutableStateFlow<ChatError?>(null)
    private val auxBusy = MutableStateFlow(false)
    private val pendingAttachments = MutableStateFlow<List<Attachment>>(emptyList())
    private val attachmentError = MutableStateFlow<AttachmentError?>(null)
    private val confirmation = MutableStateFlow<ToolCall?>(null)
    private val notice = MutableStateFlow<String?>(null)

    val notices: StateFlow<String?> = notice.asStateFlow()

    private data class SessionInfo(
        val isRunning: Boolean,
        val error: ChatError?,
        val agents: List<Agent>,
    )

    private data class ViewExtras(
        val auxBusy: Boolean,
        val pendingAttachments: List<Attachment>,
        val confirmRequest: ToolCall?,
        val plan: Plan?,
    )

    val attachmentErrors: StateFlow<AttachmentError?> = attachmentError.asStateFlow()

    val uiState: StateFlow<ChatUiState> = combine(
        conversationRepository.observeConversation(conversationId),
        conversationRepository.observeMessages(conversationId),
        streaming,
        combine(running, error, agentRepository.observeAgents()) { isRunning, currentError, agents ->
            SessionInfo(isRunning, currentError, agents)
        },
        combine(
            auxBusy,
            pendingAttachments,
            confirmation,
            planRepository.observePlan(conversationId),
        ) { busy, attachments, confirmRequest, plan ->
            ViewExtras(busy, attachments, confirmRequest, plan)
        },
    ) { conversation, messages, stream, session, extras ->
        val mode = conversation?.mode ?: AppMode.CHAT
        val agent = session.agents.firstOrNull { it.id == conversation?.agentId }
            ?: session.agents.firstOrNull { it.isDefault }
        val estimated = TokenEstimator.estimate(
            messages = messages,
            systemPrompt = renderSystemPrompt(mode, agent?.systemPrompt.orEmpty(), extras.plan?.render()),
            toolSpecs = toolCatalog.specs(mode),
            streamingText = stream.text,
        )
        val reported = messages.asReversed()
            .firstOrNull { it.usageInput != null }
            ?.usageInput
            ?.takeIf { it > 0 }
        ChatUiState(
            title = conversation?.title.orEmpty(),
            mode = mode,
            messages = messages,
            streamingText = stream.text,
            streamingThinking = stream.thinking,
            isRunning = session.isRunning,
            error = session.error,
            contextUsedTokens = maxOf(reported ?: 0L, estimated).takeIf { it > 0 },
            contextWindowTokens = contextWindowFor(agent?.provider, agent?.model ?: conversationModel(messages)),
            contextEstimated = reported == null || estimated > reported,
            auxBusy = extras.auxBusy,
            pendingAttachments = extras.pendingAttachments,
            plan = extras.plan,
            confirmRequest = extras.confirmRequest,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    private var runJob: Job? = null
    private var titleGenerated = false
    private var autoCompressAttempted = false

    init {
        viewModelScope.launch {
            confirmationQueue.requests.collect { call -> confirmation.value = call }
        }
        runNotifier.setStopHandler { runJob?.cancel() }
    }

    fun dismissNotice() {
        notice.value = null
    }

    /** Records the newest completed tool sequence as a replayable skill. */
    fun saveLastRunAsSkill(name: String, savedTemplate: String, emptyLabel: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val calls = conversationRepository.getMessages(conversationId)
                .lastOrNull { it.role == ChatRole.ASSISTANT && it.toolCalls.isNotEmpty() }
                ?.toolCalls
                ?.filter { it.status == ToolCallStatus.DONE }
            if (calls.isNullOrEmpty()) {
                notice.value = emptyLabel
                return@launch
            }
            runCatching { skillRepository.saveFromToolCalls(trimmed, calls) }
                .onSuccess { skill -> notice.value = savedTemplate.format(skill.name) }
                .onFailure { failure -> notice.value = failure.message }
        }
    }

    fun respondConfirmation(allow: Boolean) {
        val call = confirmation.value ?: return
        confirmationQueue.respond(call.id, allow)
        confirmation.value = null
    }

    private fun conversationModel(messages: List<ChatMessage>): String =
        messages.lastOrNull { it.model != null }?.model.orEmpty()

    private fun contextWindowFor(provider: ProviderId?, model: String): Long? =
        ModelCatalog.entryFor(provider ?: ProviderId.OPENAI_COMPATIBLE, model)?.contextWindow
            ?: ModelContextWindows.forModel(model)

    fun send(text: String) {
        val trimmed = text.trim()
        val attachments = pendingAttachments.value
        if ((trimmed.isEmpty() && attachments.isEmpty()) || running.value) return
        pendingAttachments.value = emptyList()
        runJob = viewModelScope.launch { executeTurn(trimmed, attachments = attachments) }
    }

    fun importImages(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val room = (MAX_IMAGE_ATTACHMENTS - pendingAttachments.value.size).coerceAtLeast(0)
            val imported = uris.take(room).mapNotNull { uri ->
                attachmentProcessor.importImage(uri).getOrElse { failure ->
                    attachmentError.value = failure.toAttachmentError()
                    null
                }
            }
            pendingAttachments.update { it + imported }
        }
    }

    fun importTextFile(uri: android.net.Uri) {
        viewModelScope.launch {
            attachmentProcessor.importTextFile(uri)
                .onSuccess { attachment ->
                    pendingAttachments.update { it + attachment }
                }
                .onFailure { failure ->
                    attachmentError.value = failure.toAttachmentError()
                }
        }
    }

    fun removePendingAttachment(id: String) {
        val removed = pendingAttachments.value.firstOrNull { it.id == id }
        pendingAttachments.update { list -> list.filterNot { it.id == id } }
        removed?.let { attachment ->
            viewModelScope.launch { attachmentProcessor.delete(attachment) }
        }
    }

    fun dismissAttachmentError() {
        attachmentError.value = null
    }

    val screenCaptureReady: StateFlow<Boolean> = screenshotProvider.ready

    fun createScreenCaptureIntent(): android.content.Intent = screenshotProvider.createPermissionIntent()

    fun onScreenPermissionResult(resultCode: Int, data: android.content.Intent): Boolean =
        screenshotProvider.onPermissionResult(resultCode, data)

    /** Captures the screen and sends it to the model as an image attachment. */
    fun analyzeScreen(prompt: String) {
        if (running.value) return
        runJob = viewModelScope.launch {
            auxBusy.value = true
            val bytes = try {
                screenshotProvider.capture().getOrElse { failure ->
                    error.value = ChatError(ChatError.Kind.SCREEN_CAPTURE, failure.message)
                    return@launch
                }
            } finally {
                auxBusy.value = false
            }
            val attachment = attachmentProcessor.importImageBytes(bytes).getOrElse { failure ->
                error.value = ChatError(ChatError.Kind.SCREEN_CAPTURE, failure.message)
                return@launch
            }
            executeTurn(prompt, attachments = listOf(attachment))
        }
    }

    fun speakMessage(text: String) {
        speechOutput.speak(text)
    }

    fun stopSpeaking() {
        speechOutput.stop()
    }

    override fun onCleared() {
        speechOutput.stop()
        runNotifier.setStopHandler(null)
        runNotifier.stopRunning()
        super.onCleared()
    }

    private fun Throwable.toAttachmentError(): AttachmentError = when (message) {
        "file_too_large" -> AttachmentError.TOO_LARGE
        "unsupported_type" -> AttachmentError.UNSUPPORTED
        else -> AttachmentError.READ_FAILED
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

    fun toggleStar(messageId: Long) {
        viewModelScope.launch {
            val message = uiState.value.messages.firstOrNull { it.id == messageId } ?: return@launch
            conversationRepository.setStarred(messageId, !message.starred)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch { conversationRepository.deleteMessage(messageId) }
    }

    fun editAndResend(messageId: Long, newText: String) {
        if (running.value) return
        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return
        runJob = viewModelScope.launch {
            conversationRepository.updateMessageContent(messageId, trimmed)
            conversationRepository.deleteMessagesAfter(conversationId, messageId)
            executeTurn(trimmed, appendUserMessage = false)
        }
    }

    /** Summarizes older history, keeping recent turns verbatim. */
    fun compressContext() {
        if (running.value || auxBusy.value) return
        viewModelScope.launch { runCompression() }
    }

    /** Extracts durable user facts from the recent conversation. */
    fun distillMemory() {
        if (running.value || auxBusy.value) return
        viewModelScope.launch {
            val config = resolveConfig() ?: return@launch
            auxBusy.value = true
            try {
                runDistillation(config)
            } finally {
                auxBusy.value = false
            }
        }
    }

    fun buildExportText(labels: ExportLabels): String {
        val state = uiState.value
        if (state.messages.isEmpty()) return labels.emptyConversation
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        return buildString {
            append("# ").append(state.title.ifBlank { labels.emptyConversation }).append("\n\n")
            append("> ").append(timestamp).append("\n\n")
            state.messages.forEach { message ->
                when (message.role) {
                    ChatRole.USER -> {
                        append("## ").append(labels.you).append("\n\n")
                        append(message.content).append("\n\n")
                    }

                    ChatRole.ASSISTANT -> {
                        if (message.content.isNotBlank()) {
                            append("## ").append(labels.assistant).append("\n\n")
                            append(message.content).append("\n\n")
                        }
                        message.thinking?.takeIf { it.isNotBlank() }?.let { thinking ->
                            append("<details><summary>").append(labels.thinking).append("</summary>\n\n")
                            append(thinking).append("\n\n</details>\n\n")
                        }
                        message.toolCalls.forEach { call ->
                            append("### ").append(labels.toolCall).append(": `").append(call.name).append("`\n\n")
                            append("```json\n").append(call.argumentsJson).append("\n```\n\n")
                            call.result?.takeIf { it.isNotBlank() }?.let { result ->
                                append("```\n").append(result).append("\n```\n\n")
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }.trim() + "\n"
    }

    private suspend fun executeTurn(
        text: String,
        appendUserMessage: Boolean = true,
        attachments: List<Attachment> = emptyList(),
    ) {
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

        val isFirstTurn = conversationRepository.getMessages(conversationId)
            .count { it.role == ChatRole.USER } == 0

        if (appendUserMessage) {
            conversationRepository.append(
                ChatMessage(
                    conversationId = conversationId,
                    role = ChatRole.USER,
                    content = text,
                    attachments = attachments,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            if (conversation.title.isBlank()) {
                conversationRepository.updateTitle(conversationId, text.take(TITLE_MAX_CHARS))
            }
        }

        val rawHistory = conversationRepository.getMessages(conversationId)
        val history = withMemories(prepareHistory(rawHistory))
        val runId = runRepository.start(conversationId, conversation.mode)
        runNotifier.startRunning(conversation.title.ifBlank { "BAIC2" })

        running.value = true
        error.value = null
        auxBusy.value = false
        var assistantMessageId: Long? = null
        var pendingCalls: List<ToolCall> = emptyList()
        var failed = false

        try {
            agentLoop.run(
                config = config,
                mode = conversation.mode,
                customSystemPrompt = agent?.systemPrompt.orEmpty(),
                history = history,
                conversationId = conversationId,
                planContext = planRepository.getPlan(conversationId)?.render(),
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
                    }

                    is AgentEvent.ToolCallFinished -> {
                        pendingCalls = pendingCalls.map { call ->
                            if (call.id == event.call.id) event.call else call
                        }
                        assistantMessageId?.let { id ->
                            conversationRepository.updateToolCalls(id, pendingCalls)
                        }
                        conversationRepository.append(
                            ChatMessage(
                                conversationId = conversationId,
                                role = ChatRole.TOOL,
                                content = event.call.result.orEmpty(),
                                toolCallId = event.call.id,
                                toolName = event.call.name,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }

                    is AgentEvent.Usage -> Unit

                    AgentEvent.Completed -> runRepository.finish(runId, RunState.COMPLETED)

                    is AgentEvent.Failed -> {
                        failed = true
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
            failed = true
            error.value = ChatError(ChatError.Kind.INTERNAL, e.message)
            runRepository.finish(runId, RunState.FAILED)
        } finally {
            running.value = false
            streaming.value = StreamingState()
            confirmation.value = null
            runNotifier.stopRunning()
        }

        if (!failed) {
            if (isFirstTurn && !titleGenerated) {
                maybeGenerateTitle(config)
            }
            maybeDistillAutomatically(config)
            maybeAutoCompress(config)
        }
    }

    /**
     * Images are only materialized for the newest user turn; older image and
     * file attachments are replaced by a short marker so history stays cheap
     * (the assistant already answered them).
     */
    private suspend fun prepareHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val lastUserId = messages.lastOrNull { it.role == ChatRole.USER }?.id
        return messages.map { message ->
            if (message.attachments.isEmpty()) return@map message
            if (message.id == lastUserId) {
                message.copy(
                    attachments = message.attachments.map { attachmentProcessor.withBase64(it) },
                )
            } else {
                val marker = message.attachments.joinToString(" ") { attachment ->
                    "[附件: ${attachment.fileName ?: attachment.kind.name}]"
                }
                message.copy(
                    content = message.content.ifBlank { marker },
                    attachments = emptyList(),
                )
            }
        }
    }

    private suspend fun withMemories(history: List<ChatMessage>): List<ChatMessage> {
        val memories = memoryRepository.list(MemoryKind.MEMORY)
        if (memories.isEmpty()) return history
        val system = ChatMessage(
            role = ChatRole.SYSTEM,
            content = "Facts you already know about the user:\n" +
                memories.joinToString("\n") { "- ${it.content}" },
        )
        return listOf(system) + history
    }

    private suspend fun maybeGenerateTitle(config: ProviderConfig) {
        val messages = conversationRepository.getMessages(conversationId)
        val firstUser = messages.firstOrNull { it.role == ChatRole.USER } ?: return
        val firstAssistant = messages.firstOrNull { it.role == ChatRole.ASSISTANT && it.content.isNotBlank() }
        titleGenerated = true
        runCatching {
            val title = auxiliaryTasks.complete(
                config = config,
                systemPrompt = AuxiliaryTasks.TITLE_SYSTEM,
                userPrompt = buildString {
                    append(firstUser.content.take(400))
                    firstAssistant?.content?.takeIf { it.isNotBlank() }?.let {
                        append("\n\nAssistant replied: ").append(it.take(200))
                    }
                },
                maxTokens = 32,
                temperature = 0.3,
            ).lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.trim('"', '\'', '。', '.', '：', ':')
                ?.take(40)
            if (!title.isNullOrBlank()) {
                conversationRepository.updateTitle(conversationId, title)
            }
        }
    }

    private suspend fun maybeDistillAutomatically(config: ProviderConfig) {
        val messages = conversationRepository.getMessages(conversationId)
        val assistantCount = messages.count { it.role == ChatRole.ASSISTANT }
        if (assistantCount == 0 || assistantCount % DISTILL_EVERY_MESSAGES != 0) return
        runDistillation(config)
    }

    private suspend fun runDistillation(config: ProviderConfig) {
        val messages = conversationRepository.getMessages(conversationId)
            .filter { it.role == ChatRole.USER || (it.role == ChatRole.ASSISTANT && it.content.isNotBlank()) }
            .takeLast(30)
        if (messages.isEmpty()) return
        val raw = runCatching {
            auxiliaryTasks.complete(
                config = config,
                systemPrompt = AuxiliaryTasks.MEMORY_SYSTEM,
                userPrompt = AuxiliaryTasks.renderTranscript(messages),
                maxTokens = 300,
                temperature = 0.2,
            )
        }.getOrNull() ?: return
        parseMemoryList(raw).forEach { fact ->
            memoryRepository.add(MemoryKind.MEMORY, fact, conversationId)
        }
    }

    private fun parseMemoryList(raw: String): List<String> {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val fromJson = runCatching {
            (json.parseToJsonElement(trimmed) as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
        }.getOrNull()
        return (fromJson ?: trimmed.lineSequence()
            .map { it.trim().trimStart('-', '*', '•').trim() }
            .filter { it.isNotBlank() && it != "[]" }
            .toList())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)
    }

    private suspend fun maybeAutoCompress(config: ProviderConfig) {
        if (autoCompressAttempted) return
        val window = ModelCatalog.entryFor(config.provider, config.model)?.contextWindow
            ?: ModelContextWindows.forModel(config.model)
            ?: return
        val messages = conversationRepository.getMessages(conversationId)
        val mode = conversationRepository.get(conversationId)?.mode ?: AppMode.CHAT
        val estimated = TokenEstimator.estimate(
            messages = messages,
            systemPrompt = renderSystemPrompt(
                mode,
                "",
                planRepository.getPlan(conversationId)?.render(),
            ),
            toolSpecs = toolCatalog.specs(mode),
        )
        val reported = messages.asReversed().firstOrNull { it.usageInput != null }?.usageInput ?: 0L
        val used = maxOf(reported, estimated)
        if (used < window * AUTO_COMPRESS_THRESHOLD) return
        autoCompressAttempted = true
        runCompression(config)
    }

    private suspend fun runCompression(config: ProviderConfig? = null) {
        val resolved = config ?: resolveConfig() ?: return
        val messages = conversationRepository.getMessages(conversationId)
        if (messages.size <= KEEP_RECENT_MESSAGES) return

        val boundary = messages.takeLast(KEEP_RECENT_MESSAGES).firstOrNull { it.role == ChatRole.USER }
            ?: return
        val older = messages.takeWhile { it.id < boundary.id }
        if (older.none { it.role == ChatRole.USER }) return

        auxBusy.value = true
        try {
            val summary = auxiliaryTasks.complete(
                config = resolved,
                systemPrompt = AuxiliaryTasks.COMPRESS_SYSTEM,
                userPrompt = AuxiliaryTasks.renderTranscript(older),
                maxTokens = 600,
                temperature = 0.2,
            )
            if (summary.isBlank()) return
            val carrier = older.first()
            conversationRepository.applyCompression(
                conversationId = conversationId,
                summaryCarrierId = carrier.id,
                keepFromMessageId = boundary.id,
                summary = summary,
            )
            memoryRepository.add(MemoryKind.SNAPSHOT, summary, conversationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error.value = ChatError(ChatError.Kind.INTERNAL, e.message)
        } finally {
            auxBusy.value = false
        }
    }

    private suspend fun resolveConfig(): ProviderConfig? {
        val conversation = conversationRepository.get(conversationId) ?: return null
        return try {
            agentRepository.resolveConfig(conversation.agentId)
        } catch (e: ApiKeyUnavailableException) {
            error.value = ChatError(ChatError.Kind.API_KEY)
            null
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
        private const val KEEP_RECENT_MESSAGES = 6
        private const val DISTILL_EVERY_MESSAGES = 10
        private const val AUTO_COMPRESS_THRESHOLD = 0.85
        private const val MAX_IMAGE_ATTACHMENTS = 4
    }
}
