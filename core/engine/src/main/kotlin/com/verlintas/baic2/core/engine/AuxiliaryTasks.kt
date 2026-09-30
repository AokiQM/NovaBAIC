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
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class AuxiliaryFailure(val error: ProviderError) : Exception(error.message)

/**
 * Small non-agent LLM tasks (titles, memory distillation, compression
 * summaries). Uses the user's own provider config, never tools.
 */
class AuxiliaryTasks(
    private val providerFactory: (ProviderId) -> ChatProvider,
) {

    suspend fun complete(
        config: ProviderConfig,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.3,
    ): String {
        val provider = providerFactory(config.provider)
        val request = ChatRequest(
            config = config.copy(
                maxTokens = maxTokens,
                temperature = temperature,
                reasoning = false,
            ),
            systemPrompt = systemPrompt,
            messages = listOf(ChatMessage(role = ChatRole.USER, content = userPrompt)),
        )
        val text = StringBuilder()
        var failure: ProviderError? = null
        provider.stream(request).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> text.append(event.text)
                is StreamEvent.Failed -> failure = event.error
                else -> Unit
            }
        }
        failure?.let { throw AuxiliaryFailure(it) }
        return text.toString().trim()
    }

    companion object {
        const val TITLE_SYSTEM =
            "You write short conversation titles. Reply with the title only: no quotes, " +
                "no trailing punctuation, at most 8 words, in the same language as the user."

        const val MEMORY_SYSTEM =
            "Extract durable facts about the user from the conversation: name, stable preferences, " +
                "ongoing projects, agreements. Reply with a JSON array of at most 5 short strings. " +
                "If nothing is durable, reply []. No commentary."

        const val COMPRESS_SYSTEM =
            "Summarize the conversation for continued context. Keep facts, decisions, open tasks and " +
                "user preferences. Be compact (at most 200 words). Reply with the summary only."

        fun renderTranscript(messages: List<ChatMessage>, perMessageLimit: Int = 500): String =
            messages.joinToString("\n") { message ->
                val role = when (message.role) {
                    ChatRole.USER -> "USER"
                    ChatRole.ASSISTANT -> "ASSISTANT"
                    ChatRole.TOOL -> "TOOL:${message.toolName ?: ""}"
                    ChatRole.SYSTEM -> "SYSTEM"
                }
                "$role: ${message.content.take(perMessageLimit)}"
            }

        /**
         * Parses a model reply into at most five durable-fact strings. Accepts a
         * clean JSON array, a fenced array, or an array wrapped in prose. A reply
         * that only *looks* like JSON yields no facts (never junk lines), while
         * plain bullet lists still work as the documented fallback.
         */
        fun parseFactList(raw: String): List<String> {
            val trimmed = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            extractJsonArray(trimmed)?.let { facts ->
                return facts.map { it.trim() }.filter { it.isNotBlank() }.take(MAX_FACTS)
            }
            // Broken JSON must not become "facts": bail instead of line-parsing it.
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) return emptyList()
            return trimmed.lineSequence()
                .map { it.trim().trimStart('-', '*', '•').trim().trim('"', '\'') }
                .filter { line ->
                    line.isNotBlank() &&
                        line != "[]" &&
                        !line.startsWith("{") &&
                        !line.startsWith("[") &&
                        !line.endsWith(":")
                }
                .take(MAX_FACTS)
                .toList()
        }

        private fun extractJsonArray(text: String): List<String>? {
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start < 0 || end <= start) return null
            return runCatching {
                val element = Json.parseToJsonElement(text.substring(start, end + 1))
                (element as? JsonArray)
                    ?.mapNotNull { item ->
                        (item as? JsonPrimitive)?.takeIf { it.isString }?.content
                    }
            }.getOrNull()
        }

        private const val MAX_FACTS = 5
    }
}
