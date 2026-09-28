package com.verlintas.baic2.core.engine

import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.RunBudget
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolCallStatus
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Tool discovery for a mode. */
interface ToolCatalog {
    fun specs(mode: AppMode): List<ToolSpec>

    fun find(name: String): ToolSpec?
}

/** Executes a single tool call. */
fun interface ToolRunner {
    suspend fun run(call: ToolCall): ToolResult
}

/** Asks the user to approve a tool call (Act mode). */
fun interface ConfirmationGate {
    suspend fun confirm(call: ToolCall): Boolean

    companion object {
        val AllowAll = ConfirmationGate { true }
    }
}

/**
 * The agent loop: stream one assistant round, execute any tool calls through
 * the mode gate and budget, append tool results, repeat until the model
 * answers without tools or a budget is exhausted.
 *
 * Invariant: every assistant message carrying tool calls is followed by one
 * tool result per call, including denied/budget-truncated ones — otherwise
 * the next request would violate the provider protocol.
 */
class AgentLoop(
    private val providerFactory: (ProviderId) -> ChatProvider,
    private val toolCatalog: ToolCatalog,
    private val toolRunner: ToolRunner,
    private val confirmationGate: ConfirmationGate = ConfirmationGate.AllowAll,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun run(
        config: ProviderConfig,
        mode: AppMode,
        customSystemPrompt: String = "",
        history: List<ChatMessage>,
    ): Flow<AgentEvent> = flow {
        val budget = RunBudget.forMode(mode)
        val startedAt = clock()
        var messages = history
        var round = 0
        var toolCallsUsed = 0

        while (true) {
            if (round >= budget.maxRounds) {
                emit(AgentEvent.Failed(AgentFailure(AgentFailure.Kind.BUDGET, "Round budget exhausted")))
                return@flow
            }
            if (clock() - startedAt > budget.maxWallClockMs) {
                emit(AgentEvent.Failed(AgentFailure(AgentFailure.Kind.BUDGET, "Time budget exhausted")))
                return@flow
            }
            round++
            emit(AgentEvent.RoundStarted(round))

            val text = StringBuilder()
            val thinking = StringBuilder()
            var toolCalls = emptyList<ToolCall>()

            val provider = try {
                providerFactory(config.provider)
            } catch (e: Exception) {
                emit(
                    AgentEvent.Failed(
                        AgentFailure(AgentFailure.Kind.UNSUPPORTED_PROVIDER, e.message ?: "Provider unavailable"),
                    ),
                )
                return@flow
            }

            try {
                provider.stream(
                    ChatRequest(
                        config = config,
                        systemPrompt = systemPromptFor(mode, customSystemPrompt),
                        messages = messages,
                        tools = toolCatalog.specs(mode),
                    ),
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            text.append(event.text)
                            emit(AgentEvent.TextDelta(event.text))
                        }
                        is StreamEvent.ThinkingDelta -> {
                            thinking.append(event.text)
                            emit(AgentEvent.ThinkingDelta(event.text))
                        }
                        is StreamEvent.ToolCallsDone -> toolCalls = event.calls
                        is StreamEvent.Usage ->
                            emit(AgentEvent.Usage(event.promptTokens, event.completionTokens))
                        is StreamEvent.Failed -> throw ProviderStreamFailure(event)
                        StreamEvent.Done -> Unit
                    }
                }
            } catch (e: ProviderStreamFailure) {
                emit(
                    AgentEvent.Failed(
                        AgentFailure(AgentFailure.Kind.PROVIDER, e.event.error.message),
                    ),
                )
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(AgentEvent.Failed(AgentFailure(AgentFailure.Kind.INTERNAL, e.message ?: "Internal error")))
                return@flow
            }

            val assistant = ChatMessage(
                role = ChatRole.ASSISTANT,
                content = text.toString(),
                thinking = thinking.toString().ifBlank { null },
                toolCalls = toolCalls,
                model = config.model,
            )
            emit(AgentEvent.AssistantMessage(assistant))

            if (toolCalls.isEmpty()) {
                emit(AgentEvent.Completed)
                return@flow
            }

            messages = messages + assistant

            for (call in toolCalls) {
                val spec = toolCatalog.find(call.name)
                val decision = gate(mode, spec)
                emit(AgentEvent.ToolCallStarted(call))

                val (status, result) = when {
                    decision is GateResult.Denied ->
                        ToolCallStatus.DENIED to ToolResult.Denied(decision.reason)

                    decision is GateResult.NeedsConfirm -> {
                        val approved = confirmationGate.confirm(call)
                        if (approved) {
                            execute(call)
                        } else {
                            ToolCallStatus.REJECTED to ToolResult.Denied("User rejected the call")
                        }
                    }

                    toolCallsUsed >= budget.maxToolCalls ->
                        ToolCallStatus.DENIED to ToolResult.Denied(
                            "Tool call budget exhausted (${budget.maxToolCalls}); answer with what you have",
                        )

                    else -> execute(call)
                }

                if (status == ToolCallStatus.DONE || status == ToolCallStatus.FAILED) {
                    toolCallsUsed++
                }

                val finished = call.copy(result = render(result), status = status)
                emit(AgentEvent.ToolCallFinished(finished))
                messages = messages + ChatMessage(
                    role = ChatRole.TOOL,
                    content = render(result),
                    toolCallId = call.id,
                    toolName = call.name,
                )
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun execute(call: ToolCall): Pair<ToolCallStatus, ToolResult> = try {
        when (val result = toolRunner.run(call)) {
            is ToolResult.Success -> ToolCallStatus.DONE to result
            is ToolResult.Failure -> ToolCallStatus.FAILED to result
            is ToolResult.Denied -> ToolCallStatus.DENIED to result
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolCallStatus.FAILED to ToolResult.Failure(e.message ?: "Tool crashed")
    }

    private fun render(result: ToolResult): String = when (result) {
        is ToolResult.Success -> result.output
        is ToolResult.Failure -> "ERROR: ${result.reason}"
        is ToolResult.Denied -> "ERROR: ${result.reason}"
    }

    private fun gate(mode: AppMode, spec: ToolSpec?): GateResult {
        if (mode == AppMode.CHAT) return GateResult.Denied("Chat mode does not execute tools")
        if (spec == null) return GateResult.Denied("Unknown tool")
        if (mode.readOnlyOnly && !spec.readOnly) return GateResult.Denied("This mode only allows read-only tools")
        if (mode.requiresConfirmation) return GateResult.NeedsConfirm
        return GateResult.Allow
    }

    private fun systemPromptFor(mode: AppMode, custom: String): String {
        val base = when (mode) {
            AppMode.CHAT ->
                "You are a helpful assistant. Answer clearly and concisely. You have no tools."

            AppMode.CHAT_PLUS ->
                "You are in research mode. You may use read-only tools to inspect information. " +
                    "Draft a short plan before acting and never attempt to modify the device."

            AppMode.ACT ->
                "You are in action mode. You can call device tools; each call is confirmed by the user first, " +
                    "so explain what you are about to do and why. Prefer the smallest safe step."

            AppMode.MAX ->
                "You are in autonomous mode. Plan the shortest path to the goal, execute tools without asking, " +
                    "verify the result after each action, and summarize what you did at the end. " +
                    "Never repeat a failed call with identical arguments."
        }
        return if (custom.isBlank()) base else "$custom\n\n$base"
    }

    private sealed interface GateResult {
        data object Allow : GateResult

        data object NeedsConfirm : GateResult

        data class Denied(val reason: String) : GateResult
    }

    private class ProviderStreamFailure(val event: StreamEvent.Failed) :
        Exception(event.error.message)
}
