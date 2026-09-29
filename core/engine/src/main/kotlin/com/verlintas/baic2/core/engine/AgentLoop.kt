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
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Tool discovery for a mode. */
interface ToolCatalog {
    fun specs(mode: AppMode): List<ToolSpec>

    fun find(name: String): ToolSpec?
}

/** Per-run information a tool may need (plan updates, sub-agents, auditing). */
data class ToolRunContext(
    val conversationId: Long? = null,
    val mode: AppMode = AppMode.CHAT,
    val config: ProviderConfig? = null,
)

/** Executes a single tool call. */
fun interface ToolRunner {
    suspend fun run(call: ToolCall, run: ToolRunContext): ToolResult
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
        conversationId: Long? = null,
        planContext: String? = null,
        budgetOverride: RunBudget? = null,
    ): Flow<AgentEvent> = flow {
        val budget = budgetOverride ?: RunBudget.forMode(mode)
        val runContext = ToolRunContext(
            conversationId = conversationId,
            mode = mode,
            config = config,
        )
        val startedAt = clock()
        var messages = history
        var round = 0
        var toolCallsUsed = 0
        val toolFailures = java.util.concurrent.ConcurrentHashMap<String, Int>()

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
            var thinkingSignature: String? = null
            var toolCalls = emptyList<ToolCall>()
            var roundUsageInput: Long? = null
            var roundUsageOutput: Long? = null

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
                        systemPrompt = renderSystemPrompt(mode, customSystemPrompt, planContext),
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
                        is StreamEvent.ThinkingSignature -> {
                            thinkingSignature = event.signature
                        }
                        is StreamEvent.ToolCallsDone -> toolCalls = event.calls
                        is StreamEvent.Usage -> {
                            roundUsageInput = event.promptTokens
                            roundUsageOutput = event.completionTokens
                            emit(AgentEvent.Usage(event.promptTokens, event.completionTokens))
                        }
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
                thinkingSignature = thinkingSignature,
                toolCalls = toolCalls,
                model = config.model,
                usageInput = roundUsageInput,
                usageOutput = roundUsageOutput,
            )
            emit(AgentEvent.AssistantMessage(assistant))

            if (toolCalls.isEmpty()) {
                emit(AgentEvent.Completed)
                return@flow
            }

            messages = messages + assistant

            val specs = toolCalls.associateWith { call -> toolCatalog.find(call.name) }
            val canParallelize = mode == AppMode.MAX && toolCalls.size > 1 &&
                toolCalls.all { call ->
                    val spec = specs[call]
                    spec?.parallelSafe == true && gate(mode, spec) is GateResult.Allow
                }

            if (canParallelize) {
                toolCalls.forEach { emit(AgentEvent.ToolCallStarted(it)) }
                val executed = executeParallel(toolCalls, runContext)
                executed.forEach { (call, status, result) ->
                    recordFailure(toolFailures, call.name, status, result)
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
            } else {
                for (call in toolCalls) {
                    val spec = specs[call]
                    val decision = gate(mode, spec)
                    emit(AgentEvent.ToolCallStarted(call))

                    val (status, result) = when {
                        decision is GateResult.Denied ->
                            ToolCallStatus.DENIED to ToolResult.Denied(decision.reason)

                        (toolFailures[call.name] ?: 0) >= MAX_TOOL_FAILURES ->
                            ToolCallStatus.DENIED to ToolResult.Denied(
                                "${call.name} failed $MAX_TOOL_FAILURES times in this run. " +
                                    "Re-read its parameter documentation, change the arguments, " +
                                    "or use another tool — do not retry unchanged.",
                            )

                        decision is GateResult.NeedsConfirm -> {
                            val approved = confirmationGate.confirm(call)
                            if (approved) {
                                execute(call, runContext)
                            } else {
                                ToolCallStatus.REJECTED to ToolResult.Denied("User rejected the call")
                            }
                        }

                        toolCallsUsed >= budget.maxToolCalls ->
                            ToolCallStatus.DENIED to ToolResult.Denied(
                                "Tool call budget exhausted (${budget.maxToolCalls}); answer with what you have",
                            )

                        else -> execute(call, runContext)
                    }

                    recordFailure(toolFailures, call.name, status, result)
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
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Tool-name level circuit breaker: three failures in a run and further
     * identical attempts are denied with guidance instead of burning rounds.
     */
    private fun recordFailure(
        failures: MutableMap<String, Int>,
        toolName: String,
        status: ToolCallStatus,
        result: ToolResult,
    ) {
        when {
            status == ToolCallStatus.DONE && result is ToolResult.Success ->
                failures.remove(toolName)

            status == ToolCallStatus.FAILED ||
                (status == ToolCallStatus.DONE && result is ToolResult.Failure) ->
                failures[toolName] = (failures[toolName] ?: 0) + 1
        }
    }

    private suspend fun executeParallel(
        toolCalls: List<ToolCall>,
        run: ToolRunContext,
    ): List<Triple<ToolCall, ToolCallStatus, ToolResult>> =
        kotlinx.coroutines.coroutineScope {
            toolCalls.map { call ->
                async {
                    val (status, result) = execute(call, run)
                    Triple(call, status, result)
                }
            }.map { it.await() }
        }

    private suspend fun execute(
        call: ToolCall,
        run: ToolRunContext,
    ): Pair<ToolCallStatus, ToolResult> = try {
        when (val result = toolRunner.run(call, run)) {
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

    private companion object {
        const val MAX_TOOL_FAILURES = 3
    }

    private sealed interface GateResult {
        data object Allow : GateResult

        data object NeedsConfirm : GateResult

        data class Denied(val reason: String) : GateResult
    }

    private class ProviderStreamFailure(val event: StreamEvent.Failed) :
        Exception(event.error.message)
}

/**
 * The system prompt for a run. Public so the chat layer can estimate the
 * context size before a request is built.
 */
fun renderSystemPrompt(mode: AppMode, custom: String, planContext: String?): String {
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
            "You are in autonomous mode. Maintain a task plan with the plan_update tool: create it " +
                "before the first action, keep exactly one step marked DOING, verify each step with an " +
                "observation tool (screen_ocr / ui_control / device_info) and update its status immediately, " +
                "then mark it DONE or FAILED. Execute tools without asking, never repeat a failed call " +
                "with identical arguments, and summarize the outcome at the end."
    }
    val withPlan = if (!planContext.isNullOrBlank() && mode == AppMode.MAX) {
        base + "\n\nCurrent plan (keep it updated via plan_update):\n" + planContext
    } else {
        base
    }
    return if (custom.isBlank()) withPlan else "$custom\n\n$withPlan"
}
