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

package com.verlintas.baic2.core.network.provider

import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ToolCall
import kotlin.test.assertEquals
import org.junit.Test

class ToolCallHistoryTest {

    private fun call(
        id: String = "call_1",
        name: String = "get_weather",
        result: String? = "sunny",
    ) = ToolCall(id = id, name = name, argumentsJson = "{}", result = result)

    private fun assistant(
        toolCalls: List<ToolCall>,
        content: String = "",
        createdAt: Long = 1_000,
    ) = ChatMessage(
        id = 10,
        conversationId = 1,
        role = ChatRole.ASSISTANT,
        content = content,
        toolCalls = toolCalls,
        createdAt = createdAt,
    )

    private fun tool(
        toolCallId: String?,
        content: String = "sunny",
        createdAt: Long = 1_001,
    ) = ChatMessage(
        conversationId = 1,
        role = ChatRole.TOOL,
        content = content,
        toolCallId = toolCallId,
        toolName = "get_weather",
        createdAt = createdAt,
    )

    @Test
    fun synthesizesMissingToolResultsFromPersistedCalls() {
        val user = ChatMessage(conversationId = 1, role = ChatRole.USER, content = "weather?", createdAt = 900)

        val sanitized = ToolCallHistory.sanitize(
            listOf(user, assistant(listOf(call()), content = "checking"), ChatMessage(conversationId = 1, role = ChatRole.USER, content = "next", createdAt = 2_000)),
        )

        assertEquals(4, sanitized.size)
        assertEquals(ChatRole.ASSISTANT, sanitized[1].role)
        assertEquals(ChatRole.TOOL, sanitized[2].role)
        assertEquals("call_1", sanitized[2].toolCallId)
        assertEquals("sunny", sanitized[2].content)
        assertEquals("get_weather", sanitized[2].toolName)
        assertEquals(ChatRole.USER, sanitized[3].role)
    }

    @Test
    fun keepsExplicitToolMessagesAndDropsOrphans() {
        val history = listOf(
            assistant(listOf(call(id = "call_1"))),
            tool("call_1", content = "explicit"),
            tool("call_orphan", content = "orphan"),
            ChatMessage(conversationId = 1, role = ChatRole.USER, content = "next", createdAt = 2_000),
        )

        val sanitized = ToolCallHistory.sanitize(history)

        assertEquals(3, sanitized.size)
        assertEquals("explicit", sanitized[1].content)
        assertEquals(ChatRole.USER, sanitized[2].role)
    }

    @Test
    fun emitsToolResultsInCallOrder() {
        val history = listOf(
            assistant(listOf(call(id = "call_a"), call(id = "call_b"))),
            tool("call_b", content = "second"),
            tool("call_a", content = "first"),
        )

        val sanitized = ToolCallHistory.sanitize(history)

        assertEquals(3, sanitized.size)
        assertEquals("call_a", sanitized[1].toolCallId)
        assertEquals("first", sanitized[1].content)
        assertEquals("call_b", sanitized[2].toolCallId)
        assertEquals("second", sanitized[2].content)
    }

    @Test
    fun replacesBlankCallIdsConsistently() {
        val sanitized = ToolCallHistory.sanitize(
            listOf(assistant(listOf(call(id = "", result = "sunny")))),
        )

        assertEquals(2, sanitized.size)
        val id = sanitized[0].toolCalls.single().id
        assertEquals(true, id.isNotBlank())
        assertEquals(id, sanitized[1].toolCallId)
    }

    @Test
    fun fillsMissingResultWithPlaceholder() {
        val sanitized = ToolCallHistory.sanitize(
            listOf(assistant(listOf(call(result = null)))),
        )

        assertEquals(2, sanitized.size)
        assertEquals("ERROR: tool result missing", sanitized[1].content)
    }

    @Test
    fun plainHistoryPassesThroughUnchanged() {
        val history = listOf(
            ChatMessage(conversationId = 1, role = ChatRole.USER, content = "hi", createdAt = 1),
            ChatMessage(conversationId = 1, role = ChatRole.ASSISTANT, content = "hello", createdAt = 2),
        )

        assertEquals(history, ToolCallHistory.sanitize(history))
    }
}
