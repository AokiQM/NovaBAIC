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

package com.verlintas.baic2.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TokenEstimatorTest {

    @Test
    fun countsCjkPerCharacterAndLatinPerFour() {
        assertEquals(4, TokenEstimator.text("你好世界"))
        assertEquals(2, TokenEstimator.text("abcdefgh"))
        assertEquals(3, TokenEstimator.text("你好abcd"))
        assertEquals(0, TokenEstimator.text(""))
    }

    @Test
    fun messageEstimateIncludesToolCallsAndAttachments() {
        val plain = ChatMessage(role = ChatRole.USER, content = "hello")
        val rich = ChatMessage(
            role = ChatRole.ASSISTANT,
            content = "hi",
            toolCalls = listOf(
                ToolCall(
                    id = "call_1",
                    name = "device_info",
                    argumentsJson = "{}",
                    result = "Android 15",
                ),
            ),
            attachments = listOf(
                Attachment(id = "a", kind = AttachmentKind.TEXT, mimeType = "text/plain", text = "note"),
            ),
        )
        assertTrue(TokenEstimator.message(rich) > TokenEstimator.message(plain))
    }

    @Test
    fun contextEstimateGrowsWithHistoryAndTools() {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "hello"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "hi there"),
        )
        val specs = listOf(ToolSpec(name = "device_info", description = "device facts"))
        val base = TokenEstimator.estimate(messages)
        val withSystem = TokenEstimator.estimate(messages, systemPrompt = "You are helpful.")
        val withTools = TokenEstimator.estimate(messages, toolSpecs = specs)
        val withStream = TokenEstimator.estimate(messages, streamingText = "partial answer")
        assertTrue(withSystem > base)
        assertTrue(withTools > base)
        assertTrue(withStream > base)
    }
}
