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

package com.verlintas.baic2.eval

import com.verlintas.baic2.core.engine.AgentFailure
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.StreamEvent
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec

private fun textRound(vararg chunks: String): List<StreamEvent> =
    chunks.map { StreamEvent.TextDelta(it) } + StreamEvent.Done

private fun toolRound(vararg calls: ToolCall): List<StreamEvent> =
    listOf(StreamEvent.ToolCallsDone(calls.toList()), StreamEvent.Done)

private fun readWeatherSpec() = ToolSpec(
    name = "read_weather",
    description = "Read the weather for a city",
    parametersJson = """{"type":"object","properties":{"city":{"type":"string"}}}""",
    readOnly = true,
)

private fun setVolumeSpec() = ToolSpec(
    name = "set_volume",
    description = "Set the media volume",
    parametersJson = """{"type":"object","properties":{"level":{"type":"integer"}}}""",
)

/**
 * The starting eval set. Each scenario is a regression gate: it encodes one
 * behavior of the agent loop that must keep working.
 */
object ScenarioCatalog {

    fun builtIn(): List<Scenario> = listOf(
        Scenario(
            id = "simple_chat",
            description = "Answer a plain question without tools",
            mode = AppMode.CHAT,
            rounds = listOf(textRound("Hello", " from ", "Nova")),
            expect = Expectations(
                finalTextContains = listOf("Nova"),
                maxRounds = 1,
            ),
        ),
        Scenario(
            id = "tool_then_answer",
            description = "Use a read-only tool, then answer with the result",
            mode = AppMode.MAX,
            rounds = listOf(
                toolRound(ToolCall(id = "c1", name = "read_weather", argumentsJson = """{"city":"SF"}""")),
                textRound("It is 18C in SF"),
            ),
            tools = listOf(FakeTool(readWeatherSpec(), listOf(ToolResult.Success("18C")))),
            expect = Expectations(
                finalTextContains = listOf("18C"),
                requiredToolCalls = listOf("read_weather"),
                maxRounds = 2,
            ),
        ),
        Scenario(
            id = "tool_failure_recovery",
            description = "A failed tool call is fed back and retried with another plan",
            mode = AppMode.MAX,
            rounds = listOf(
                toolRound(ToolCall(id = "c1", name = "set_volume", argumentsJson = """{"level":5}""")),
                toolRound(ToolCall(id = "c2", name = "set_volume", argumentsJson = """{"level":3}""")),
                textRound("Volume set after permission was granted"),
            ),
            tools = listOf(
                FakeTool(
                    setVolumeSpec(),
                    listOf(
                        ToolResult.Failure("permission missing: grant Modify system settings first"),
                        ToolResult.Success("volume set"),
                    ),
                ),
            ),
            expect = Expectations(
                finalTextContains = listOf("Volume set"),
                requiredToolCalls = listOf("set_volume", "set_volume"),
                maxRounds = 3,
            ),
        ),
        Scenario(
            id = "budget_guard",
            description = "Endless tool calls hit the round budget instead of looping forever",
            mode = AppMode.ACT,
            rounds = List(64) {
                toolRound(ToolCall(id = "c$it", name = "read_weather", argumentsJson = "{}"))
            },
            tools = listOf(FakeTool(readWeatherSpec(), listOf(ToolResult.Success("sunny")))),
            expect = Expectations(
                completed = false,
                failureKind = AgentFailure.Kind.BUDGET,
            ),
        ),
    )
}
