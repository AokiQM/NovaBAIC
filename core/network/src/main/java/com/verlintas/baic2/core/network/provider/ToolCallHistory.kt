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

/**
 * All three providers reject a history where an assistant message with
 * `tool_calls` is not immediately followed by one tool result per call
 * ("insufficient tool messages following tool_calls message"). Persisted
 * conversations therefore must be repaired before they cross the wire:
 *
 *  - every tool call gets exactly one result message, in call order;
 *  - results are taken from explicit tool messages when present, otherwise
 *    reconstructed from the result embedded in the persisted tool call;
 *  - tool messages that answer nothing (or answer an already-answered call)
 *    are dropped;
 *  - blank call ids are replaced with stable synthetic ids.
 *
 * Valid histories pass through unchanged.
 */
object ToolCallHistory {

    private const val MISSING_RESULT = "ERROR: tool result missing"

    fun sanitize(messages: List<ChatMessage>): List<ChatMessage> {
        val needsRepair = messages.any { message ->
            message.role == ChatRole.TOOL ||
                (message.role == ChatRole.ASSISTANT && message.toolCalls.isNotEmpty())
        }
        if (!needsRepair) return messages

        val output = ArrayList<ChatMessage>(messages.size)
        var index = 0
        while (index < messages.size) {
            val message = messages[index]

            if (message.role == ChatRole.TOOL) {
                // Orphan tool message: nothing claims it, providers reject it.
                index++
                continue
            }

            if (message.role != ChatRole.ASSISTANT || message.toolCalls.isEmpty()) {
                output += message
                index++
                continue
            }

            val calls = normalizeCalls(message)
            output += message.copy(toolCalls = calls)

            val following = LinkedHashMap<String, ChatMessage>()
            var cursor = index + 1
            while (cursor < messages.size && messages[cursor].role == ChatRole.TOOL) {
                val tool = messages[cursor]
                val id = tool.toolCallId
                if (id != null && calls.any { it.id == id } && id !in following) {
                    following[id] = tool
                }
                cursor++
            }

            calls.forEach { call ->
                output += following[call.id] ?: synthesizedResult(message, call)
            }
            index = cursor
        }
        return output
    }

    private fun normalizeCalls(message: ChatMessage): List<ToolCall> =
        message.toolCalls
            .mapIndexed { position, call ->
                if (call.id.isNotBlank()) call else call.copy(id = "call_${message.id}_$position")
            }
            .distinctBy { it.id }

    private fun synthesizedResult(message: ChatMessage, call: ToolCall): ChatMessage = ChatMessage(
        conversationId = message.conversationId,
        role = ChatRole.TOOL,
        content = call.result?.takeIf { it.isNotBlank() } ?: MISSING_RESULT,
        toolCallId = call.id,
        toolName = call.name,
        createdAt = message.createdAt + 1,
    )
}
