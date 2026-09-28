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

import com.verlintas.baic2.core.model.ChatMessage
import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ChatRequest
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.ProviderConfig
import com.verlintas.baic2.core.model.ProviderError
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.model.StreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest

class AuxiliaryTasksTest {

    private val config = ProviderConfig(
        provider = ProviderId.OPENAI_COMPATIBLE,
        baseUrl = "http://localhost/v1",
        apiKey = "test",
        model = "test-model",
    )

    private class ScriptedProvider(private val events: List<StreamEvent>) : ChatProvider {
        var lastRequest: ChatRequest? = null

        override fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
            lastRequest = request
            events.forEach { emit(it) }
        }
    }

    @Test
    fun collectsTextAndForcesNoTools() = runTest {
        val provider = ScriptedProvider(
            listOf(
                StreamEvent.ThinkingDelta("ignored"),
                StreamEvent.TextDelta("Hello "),
                StreamEvent.TextDelta("title"),
                StreamEvent.Done,
            ),
        )
        val tasks = AuxiliaryTasks({ provider })

        val result = tasks.complete(config, "system", "user", maxTokens = 64, temperature = 0.1)

        assertEquals("Hello title", result)
        val request = requireNotNull(provider.lastRequest)
        assertTrue(request.tools.isEmpty())
        assertEquals(64, request.config.maxTokens)
        assertEquals(0.1, request.config.temperature)
        assertTrue(!request.config.reasoning)
    }

    @Test
    fun providerFailureBecomesAuxiliaryFailure() = runTest {
        val provider = ScriptedProvider(
            listOf(StreamEvent.Failed(ProviderError(ProviderError.Kind.AUTH, "bad key"))),
        )
        val tasks = AuxiliaryTasks({ provider })

        val failure = assertFailsWith<AuxiliaryFailure> {
            tasks.complete(config, "system", "user")
        }

        assertEquals("bad key", failure.error.message)
    }

    @Test
    fun transcriptRenderingIsCompact() {
        val messages = listOf(
            ChatMessage(role = ChatRole.USER, content = "hi"),
            ChatMessage(role = ChatRole.ASSISTANT, content = "a".repeat(600)),
            ChatMessage(role = ChatRole.TOOL, content = "result", toolName = "get_weather"),
        )

        val rendered = AuxiliaryTasks.renderTranscript(messages, perMessageLimit = 10)

        assertEquals(3, rendered.lines().size)
        assertTrue(rendered.startsWith("USER: hi"))
        assertTrue(rendered.contains("ASSISTANT: aaaaaaaaaa"))
        assertTrue(rendered.contains("TOOL:get_weather:"))
    }
}
