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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class AuxiliaryFailure(val error: ProviderError) : Exception(error.message)

/** What the curator decided to remember, revise, forget or refresh. */
data class CuratorNote(
    val kind: String,
    val content: String,
    val importance: Int,
    val whenRaw: String?,
)

data class CuratorRevise(
    val id: Long,
    val content: String?,
    val importance: Int?,
)

data class CuratorPlan(
    val remember: List<CuratorNote> = emptyList(),
    val revise: List<CuratorRevise> = emptyList(),
    val forget: List<Long> = emptyList(),
    val coreUser: String? = null,
    val coreContext: String? = null,
) {
    val isEmpty: Boolean
        get() = remember.isEmpty() && revise.isEmpty() && forget.isEmpty() &&
            coreUser == null && coreContext == null
}

/**
 * Small non-agent LLM tasks (titles, memory curation, compression
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

        const val COMPRESS_SYSTEM =
            "Summarize the conversation for continued context. Keep facts, decisions, open tasks and " +
                "user preferences. Be compact (at most 200 words). Reply with the summary only."

        /**
         * The curator is the agent's sleep-time consolidation: it reads recent
         * episodes and turns them into durable notes, just like a brain does
         * overnight. Writing is deliberate and lossless upstream - the raw
         * transcript is never deleted.
         */
        const val CURATOR_SYSTEM = "You are the memory curator. Read the recent conversation, the " +
            "existing notes and the core memory, then decide what deserves to be kept, corrected " +
            "or forgotten.\n\n" +
            "Reply with ONE JSON object and nothing else:\n" +
            "{\"remember\":[{\"kind\":\"preference\",\"content\":\"...\",\"importance\":3," +
            "\"when\":\"2026-09-12\"}],\"revise\":[{\"id\":12,\"content\":\"...\"," +
            "\"importance\":4}],\"forget\":[9],\"core_user\":\"...\",\"core_context\":\"...\"}\n\n" +
            "Rules:\n" +
            "- remember: at most 5 durable, high-value items (stable preferences, ongoing projects, " +
            "agreements, important dates, corrections). Never small talk, one-off details, tool " +
            "output, or secrets (passwords, tokens, card numbers).\n" +
            "- revise: fix or sharpen an existing note by its #id when new information updates it; " +
            "prefer revise over remember whenever a note already covers the topic, even if the " +
            "wording differs; include only the fields that change.\n" +
            "- forget: ids of notes that are clearly obsolete or contradicted, and of near-duplicates " +
            "that say the same thing in different words (keep the clearest one). Never forget what " +
            "the user asked to keep.\n" +
            "- core_user: the user's stable identity in <=600 chars (name, languages, enduring " +
            "preferences, how they like to be helped). Preserve existing lines unless they are wrong; " +
            "include it only when it changed.\n" +
            "- core_context: what is going on these days in <=600 chars (current projects, near-term " +
            "events). Refresh freely; include it only when it changed.\n" +
            "- Omit keys that have nothing to report; every content string <=400 chars, single line."

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

        /** The curator's view: core memory, note inventory, recent episodes. */
        fun renderCuratorPrompt(transcript: String, inventory: String, coreBlocks: String): String =
            buildString {
                append("Core memory now:\n").append(coreBlocks).append("\n\n")
                append("Existing notes:\n").append(inventory).append("\n\n")
                append("Recent conversation (oldest first):\n").append(transcript)
            }

        /**
         * Parses the curator reply. Anything unparseable yields an empty plan,
         * never junk notes; every field is clamped to its budget.
         */
        fun parseCuratorPlan(raw: String): CuratorPlan {
            val element = extractJsonObject(raw) ?: return CuratorPlan()
            val remember = (element["remember"] as? JsonArray).orEmpty()
                .mapNotNull { item ->
                    val objectItem = item as? JsonObject ?: return@mapNotNull null
                    val content = objectItem.string("content")?.trim().orEmpty()
                    if (content.isEmpty()) return@mapNotNull null
                    CuratorNote(
                        kind = objectItem.string("kind").orEmpty(),
                        content = content.take(MAX_NOTE_CHARS),
                        importance = (objectItem["importance"] as? JsonPrimitive)?.intOrNull
                            ?.coerceIn(1, 5) ?: 3,
                        whenRaw = objectItem.string("when")?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
                .take(MAX_REMEMBER)
            val revise = (element["revise"] as? JsonArray).orEmpty()
                .mapNotNull { item ->
                    val objectItem = item as? JsonObject ?: return@mapNotNull null
                    val id = (objectItem["id"] as? JsonPrimitive)?.longOrNull
                        ?.takeIf { it > 0 } ?: return@mapNotNull null
                    val content = objectItem.string("content")?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.take(MAX_NOTE_CHARS)
                    val importance = (objectItem["importance"] as? JsonPrimitive)?.intOrNull
                        ?.coerceIn(1, 5)
                    if (content == null && importance == null) return@mapNotNull null
                    CuratorRevise(id = id, content = content, importance = importance)
                }
                .take(MAX_REVISE)
            val forget = (element["forget"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.longOrNull?.takeIf { id -> id > 0 } }
                .take(MAX_FORGET)
            return CuratorPlan(
                remember = remember,
                revise = revise,
                forget = forget,
                coreUser = element.string("core_user")?.trim()?.takeIf { it.isNotEmpty() }
                    ?.take(MAX_CORE_CHARS),
                coreContext = element.string("core_context")?.trim()?.takeIf { it.isNotEmpty() }
                    ?.take(MAX_CORE_CHARS),
            )
        }

        private fun JsonObject.string(key: String): String? =
            (this[key] as? JsonPrimitive)?.contentOrNull

        private fun extractJsonObject(text: String): JsonObject? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return runCatching {
                Json.parseToJsonElement(text.substring(start, end + 1)) as? JsonObject
            }.getOrNull()
        }

        private const val MAX_REMEMBER = 5
        private const val MAX_REVISE = 6
        private const val MAX_FORGET = 8
        private const val MAX_NOTE_CHARS = 400
        private const val MAX_CORE_CHARS = 600
    }
}
