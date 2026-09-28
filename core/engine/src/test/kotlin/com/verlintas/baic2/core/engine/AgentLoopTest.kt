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
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolCallStatus
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class AgentLoopTest {

    private val config = ProviderConfig(
        provider = ProviderId.OPENAI_COMPATIBLE,
        baseUrl = "http://localhost/v1",
        apiKey = "test",
        model = "test-model",
    )

    private val history = listOf(ChatMessage(role = ChatRole.USER, content = "hi"))

    private class ScriptedProvider(private val rounds: List<List<StreamEvent>>) : ChatProvider {
        private var cursor = 0

        override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
            val script = rounds.getOrElse(cursor) { emptyList() }
            cursor++
            script.forEach { emit(it) }
        }
    }

    private class FakeCatalog(private val specs: List<ToolSpec>) : ToolCatalog {
        override fun specs(mode: AppMode): List<ToolSpec> = specs

        override fun find(name: String): ToolSpec? = specs.firstOrNull { it.name == name }
    }

    private fun textRound(vararg chunks: String): List<StreamEvent> =
        chunks.map { StreamEvent.TextDelta(it) } + StreamEvent.Done

    private fun toolRound(vararg calls: ToolCall): List<StreamEvent> =
        listOf(StreamEvent.ToolCallsDone(calls.toList()), StreamEvent.Done)

    @Test
    fun simpleReplyCompletes() = runTest {
        val loop = AgentLoop(
            providerFactory = { ScriptedProvider(listOf(textRound("Hello", " world"))) },
            toolCatalog = FakeCatalog(emptyList()),
            toolRunner = { _, _ -> error("must not run") },
        )

        val events = loop.run(config, AppMode.CHAT, history = history).toList()

        assertTrue(events.first() is AgentEvent.RoundStarted)
        assertEquals(
            listOf("Hello", " world"),
            events.filterIsInstance<AgentEvent.TextDelta>().map { it.text },
        )
        val assistant = events.filterIsInstance<AgentEvent.AssistantMessage>().single()
        assertEquals("Hello world", assistant.message.content)
        assertEquals(AgentEvent.Completed, events.last())
    }

    @Test
    fun toolRoundExecutesAndFinishes() = runTest {
        val call = ToolCall(id = "c1", name = "read_thing", argumentsJson = "{}")
        val provider = ScriptedProvider(
            listOf(
                toolRound(call),
                textRound("done"),
            ),
        )
        var runnerCalls = 0
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "read_thing", description = "r", readOnly = true))),
            toolRunner = { _, _ ->
                runnerCalls++
                ToolResult.Success("42")
            },
        )

        val events = loop.run(config, AppMode.MAX, history = history).toList()

        assertEquals(1, runnerCalls)
        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertEquals(ToolCallStatus.DONE, finished.call.status)
        assertEquals("42", finished.call.result)
        assertEquals(2, events.filterIsInstance<AgentEvent.RoundStarted>().size)
        assertEquals(AgentEvent.Completed, events.last())
    }

    @Test
    fun readOnlyGateDeniesWriteToolsInChatPlusMode() = runTest {
        val call = ToolCall(id = "c1", name = "write_thing", argumentsJson = "{}")
        val provider = ScriptedProvider(listOf(toolRound(call), textRound("ok")))
        var runnerCalls = 0
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "write_thing", description = "w", readOnly = false))),
            toolRunner = { _, _ -> runnerCalls++; ToolResult.Success("nope") },
        )

        val events = loop.run(config, AppMode.CHAT_PLUS, history = history).toList()

        assertEquals(0, runnerCalls)
        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertEquals(ToolCallStatus.DENIED, finished.call.status)
        assertTrue(events.last() == AgentEvent.Completed)
    }

    @Test
    fun actModeRequiresConfirmationAndFeedsRejectionBack() = runTest {
        val call = ToolCall(id = "c1", name = "do_thing", argumentsJson = "{}")
        val provider = ScriptedProvider(listOf(toolRound(call), textRound("understood")))
        var runnerCalls = 0
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "do_thing", description = "d"))),
            toolRunner = { _, _ -> runnerCalls++; ToolResult.Success("done") },
            confirmationGate = ConfirmationGate { false },
        )

        val events = loop.run(config, AppMode.ACT, history = history).toList()

        assertEquals(0, runnerCalls)
        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertEquals(ToolCallStatus.REJECTED, finished.call.status)
        assertEquals(AgentEvent.Completed, events.last())
    }

    @Test
    fun roundBudgetStopsEndlessToolCalls() = runTest {
        val call = ToolCall(id = "c1", name = "loop_thing", argumentsJson = "{}")
        val loop = AgentLoop(
            providerFactory = { ScriptedProvider(List(64) { toolRound(call) }) },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "loop_thing", description = "l"))),
            toolRunner = { _, _ -> ToolResult.Success("again") },
            confirmationGate = ConfirmationGate { true },
        )

        val events = loop.run(config, AppMode.ACT, history = history).toList()

        val failure = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(AgentFailure.Kind.BUDGET, failure.error.kind)
        assertEquals(
            com.verlintas.baic2.core.model.RunBudget.forMode(AppMode.ACT).maxRounds,
            events.filterIsInstance<AgentEvent.AssistantMessage>().size,
        )
        assertEquals(
            events.filterIsInstance<AgentEvent.ToolCallFinished>().size,
            events.filterIsInstance<AgentEvent.AssistantMessage>().sumOf { it.message.toolCalls.size },
        )
    }

    @Test
    fun providerFailureSurfacesTypedFailure() = runTest {
        val provider = ScriptedProvider(
            listOf(
                listOf(StreamEvent.Failed(ProviderError(ProviderError.Kind.AUTH, "bad key", httpStatus = 401))),
            ),
        )
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(emptyList()),
            toolRunner = { _, _ -> error("must not run") },
        )

        val events = loop.run(config, AppMode.CHAT, history = history).toList()

        val failure = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(AgentFailure.Kind.PROVIDER, failure.error.kind)
        assertEquals("bad key", failure.error.message)
        assertTrue(events.none { it == AgentEvent.Completed })
    }

    @Test
    fun unsupportedProviderIsReported() = runTest {
        val loop = AgentLoop(
            providerFactory = { throw UnsupportedOperationException("not yet") },
            toolCatalog = FakeCatalog(emptyList()),
            toolRunner = { _, _ -> error("must not run") },
        )

        val events = loop.run(config, AppMode.CHAT, history = history).toList()

        val failure = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(AgentFailure.Kind.UNSUPPORTED_PROVIDER, failure.error.kind)
    }

    @Test
    fun parallelSafeCallsRunConcurrentlyInMaxMode() = runTest {
        val first = ToolCall(id = "c1", name = "read_a", argumentsJson = "{}")
        val second = ToolCall(id = "c2", name = "read_b", argumentsJson = "{}")
        val provider = ScriptedProvider(
            listOf(toolRound(first, second), textRound("done")),
        )
        val running = java.util.concurrent.atomic.AtomicInteger(0)
        val maxConcurrent = java.util.concurrent.atomic.AtomicInteger(0)
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(
                listOf(
                    ToolSpec(name = "read_a", description = "a", readOnly = true, parallelSafe = true),
                    ToolSpec(name = "read_b", description = "b", readOnly = true, parallelSafe = true),
                ),
            ),
            toolRunner = { _, _ ->
                val now = running.incrementAndGet()
                maxConcurrent.updateAndGet { maxOf(it, now) }
                kotlinx.coroutines.delay(120)
                running.decrementAndGet()
                ToolResult.Success("ok")
            },
        )

        val events = loop.run(config, AppMode.MAX, history = history).toList()

        assertEquals(2, maxConcurrent.get(), "parallel-safe calls should overlap")
        assertEquals(2, events.filterIsInstance<AgentEvent.ToolCallFinished>().size)
        assertEquals(AgentEvent.Completed, events.last())
    }

    @Test
    fun budgetOverrideCapsRounds() = runTest {
        val call = ToolCall(id = "c1", name = "loop_thing", argumentsJson = "{}")
        val loop = AgentLoop(
            providerFactory = { ScriptedProvider(List(32) { toolRound(call) }) },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "loop_thing", description = "l"))),
            toolRunner = { _, _ -> ToolResult.Success("again") },
            confirmationGate = ConfirmationGate { true },
        )

        val events = loop.run(
            config,
            AppMode.MAX,
            history = history,
            budgetOverride = com.verlintas.baic2.core.model.RunBudget(
                maxRounds = 3,
                maxToolCalls = 10,
                maxWallClockMs = 60_000,
            ),
        ).toList()

        val failure = events.filterIsInstance<AgentEvent.Failed>().single()
        assertEquals(AgentFailure.Kind.BUDGET, failure.error.kind)
        assertEquals(3, events.filterIsInstance<AgentEvent.AssistantMessage>().size)
    }

    @Test
    fun toolCrashBecomesFailedStatus() = runTest {
        val call = ToolCall(id = "c1", name = "crash_thing", argumentsJson = "{}")
        val provider = ScriptedProvider(listOf(toolRound(call), textRound("recovered")))
        val loop = AgentLoop(
            providerFactory = { provider },
            toolCatalog = FakeCatalog(listOf(ToolSpec(name = "crash_thing", description = "c"))),
            toolRunner = { _, _ -> throw IllegalStateException("boom") },
        )

        val events = loop.run(config, AppMode.MAX, history = history).toList()

        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertEquals(ToolCallStatus.FAILED, finished.call.status)
        assertTrue(finished.call.result.orEmpty().startsWith("ERROR:"))
        assertEquals(AgentEvent.Completed, events.last())
    }
}
