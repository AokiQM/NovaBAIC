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

package com.verlintas.baic2.tools.memory

import com.verlintas.baic2.core.data.repository.ConversationRepository
import com.verlintas.baic2.core.data.repository.MemoryRepository
import com.verlintas.baic2.core.model.ChatRole
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.MemoryScoring
import com.verlintas.baic2.core.model.MemoryText
import com.verlintas.baic2.core.model.MessageHit
import com.verlintas.baic2.core.model.Note
import com.verlintas.baic2.core.model.NoteKind
import com.verlintas.baic2.core.model.NoteSource
import com.verlintas.baic2.core.model.ScoredNote
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Cue-driven episodic + semantic recall. Available in every mode because it
 * touches nothing outside the agent's own memory.
 */
class MemorySearchTool(
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "memory_search",
        description = "Search your long-term memory: durable notes and every past conversation. " +
            "Use it whenever the user refers to the past or before saying you don't know " +
            "something about them. Results carry ids for memory_read and memory_write.",
        parametersJson = """
            {"type":"object","properties":{
              "query":{"type":"string","description":"keywords in the user's language; space-separated terms are ANDed. Omit to browse recent items."},
              "scope":{"type":"string","enum":["all","notes","messages"],"description":"default all"},
              "role":{"type":"string","enum":["any","user","assistant"],"description":"filter message hits by speaker; user recalls what the user themselves said"},
              "entity":{"type":"string","description":"recall everything about a person/project/place by exact name"},
              "conversation":{"type":"string","description":"limit to one conversation by title or id"},
              "from":{"type":"string","description":"lower bound, ISO date like 2026-09-01"},
              "to":{"type":"string","description":"upper bound, ISO date"},
              "limit":{"type":"integer","description":"1-20, default 10"},
              "offset":{"type":"integer","description":"paging"}
            }}
        """.trimIndent(),
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
        alwaysAvailable = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val query = arguments.string("query")?.trim().orEmpty()
        val scope = arguments.string("scope")?.trim()?.lowercase() ?: "all"
        val from = MemoryText.parseWhen(arguments.string("from"))
        val to = endOfDay(arguments.string("to"))
        val conversation = arguments.string("conversation")?.trim()?.takeIf { it.isNotEmpty() }
        val entity = arguments.string("entity")?.trim()?.takeIf { it.isNotEmpty() }
        val roleFilter = when (arguments.string("role")?.trim()?.lowercase()) {
            "user" -> ChatRole.USER
            "assistant" -> ChatRole.ASSISTANT
            else -> null
        }
        val limit = (arguments.int("limit") ?: 10).coerceIn(1, 20)
        val offset = (arguments.int("offset") ?: 0).coerceAtLeast(0)
        val terms = MemoryText.terms(query)

        val entityNotes: List<ScoredNote> = if (entity != null && scope != "messages") {
            memoryRepository.recallByEntity(listOf(entity), limit = NOTES_PER_PAGE)
        } else {
            emptyList()
        }
        val cuedNotes: List<ScoredNote> = when {
            scope == "messages" -> emptyList()
            terms.isEmpty() && entity != null -> emptyList()
            else -> memoryRepository.recall(terms = terms, limit = NOTES_PER_PAGE, offset = offset)
        }
        val notes = (entityNotes + cuedNotes).distinctBy { it.note.id }.take(NOTES_PER_PAGE)
        val hits: List<MessageHit> = when {
            scope == "notes" -> emptyList()
            // An entity lookup with no keywords is about notes, not messages.
            terms.isEmpty() && entity != null -> emptyList()
            else -> conversationRepository.searchMessages(
                terms = terms,
                conversationQuery = conversation,
                from = from,
                to = to,
                roleFilter = roleFilter,
                limit = limit,
                offset = offset,
            )
        }

        if (notes.isEmpty() && hits.isEmpty()) {
            return ToolResult.Success(
                "No memories match" + if (query.isNotEmpty()) " \"$query\"" else "" +
                    ". Try fewer or different keywords, a wider date range, or no conversation filter.",
            )
        }
        val now = System.currentTimeMillis()
        return ToolResult.Success(
            buildString {
                append("Memory recall")
                if (query.isNotEmpty()) append(" for \"").append(query).append('"')
                append(" (notes ").append(notes.size).append(", messages ").append(hits.size).append("):\n")
                if (notes.isNotEmpty()) {
                    append("\nNotes (use the #id with memory_write replaces= to correct one):\n")
                    notes.forEach { scored ->
                        append("- ")
                        if (scored.spread) {
                            if (scored.spreadHops >= 2) {
                                append("(2-hop via #").append(scored.spreadFrom).append(") ")
                            } else {
                                append("(associated from #").append(scored.spreadFrom)
                                    .append(", link w").append(scored.linkWeight.toInt()).append(") ")
                            }
                        }
                        append(noteLine(scored, terms.size, now)).append('\n')
                    }
                } else if (hits.isNotEmpty() && scope != "messages") {
                    append("\nNo notes matched; if this is worth keeping, consider memory_write.\n")
                }
                if (hits.isNotEmpty()) {
                    append("\nMessages (use memory_read with conversation_id + message_id for more):\n")
                    hits.forEach { hit ->
                        append("- ").append(MemoryText.formatDateTime(hit.createdAt))
                            .append(" (").append(MemoryText.relativeTime(now, hit.createdAt)).append(")")
                            .append(" · \"").append(hit.conversationTitle.ifBlank { "untitled" })
                            .append("\" conv #").append(hit.conversationId)
                            .append(" · ").append(hit.role.name.lowercase()).append(" #").append(hit.messageId)
                            .append(": ").append(MemoryText.snippet(hit.content, terms)).append('\n')
                    }
                }
            }.trim(),
        )
    }

    /**
     * Every recalled note states why it surfaced: score, cue coverage,
     * strength, current retrievability and when it was last recalled - so the
     * agent can weigh a strongly-held old memory against a fresh keyword hit.
     */
    private fun noteLine(scored: ScoredNote, cueCount: Int, now: Long): String {
        val note = scored.note
        val recall = (MemoryScoring.retrievability(note, now) * 100).toInt()
        val last = if (note.lastAccessedAt > 0) {
            MemoryText.relativeTime(now, note.lastAccessedAt)
        } else {
            "never"
        }
        val cues = if (cueCount == 0) {
            "browse"
        } else {
            "cues ${scored.hits}/$cueCount" +
                scored.matchedCues.joinToString(prefix = " (", postfix = ")")
        }
        val score = "%.2f".format(java.util.Locale.ROOT, scored.score)
        val strength = "%.1f".format(java.util.Locale.ROOT, note.strength)
        val source = if (note.source != NoteSource.USER) " · src ${note.source.wire()}" else ""
        val entities = if (note.entities.isNotEmpty()) {
            note.entities.joinToString(prefix = " · @", separator = "@")
        } else {
            ""
        }
        return "#${note.id} [${note.kind.wire()} i${note.importance}$source$entities · score $score · $cues" +
            " · strength $strength · recall $recall% · last recalled $last] " +
            note.content.replace('\n', ' ')
    }

    /** A bare date as `to` means the whole day. */
    private fun endOfDay(raw: String?): Long? {
        val value = MemoryText.parseWhen(raw) ?: return null
        return if (raw != null && raw.trim().length <= 10) value + 86_400_000L - 1 else value
    }

    private companion object {
        const val NOTES_PER_PAGE = 8
    }
}

/** Opens the original episode around a recalled message. */
class MemoryReadTool(
    private val conversationRepository: ConversationRepository,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "memory_read",
        description = "Read a conversation window around a message id returned by memory_search, " +
            "or the latest messages of a conversation.",
        parametersJson = """
            {"type":"object","properties":{
              "conversation_id":{"type":"integer","description":"from memory_search"},
              "message_id":{"type":"integer","description":"anchor; omit for the latest messages"},
              "count":{"type":"integer","description":"2-20, default 6"}
            },"required":["conversation_id"]}
        """.trimIndent(),
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
        alwaysAvailable = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val conversationId = arguments.long("conversation_id")
            ?: return ToolResult.Failure("Missing 'conversation_id' argument")
        val anchor = arguments.long("message_id")
        val count = (arguments.int("count") ?: 6).coerceIn(2, 20)
        val title = conversationRepository.conversationTitle(conversationId)
            ?: return ToolResult.Failure("Conversation $conversationId does not exist.")
        val messages = conversationRepository.readAround(conversationId, anchor, count)
        if (messages.isEmpty()) {
            return ToolResult.Success("Conversation \"$title\" ($conversationId) has no messages.")
        }
        return ToolResult.Success(
            buildString {
                append("Conversation \"").append(title.ifBlank { "untitled" })
                    .append("\" (#").append(conversationId).append(')')
                if (anchor != null) append(", around message #").append(anchor)
                append(":\n")
                messages.forEach { message ->
                    append('[').append(MemoryText.formatDateTime(message.createdAt)).append("] ")
                    append(message.role.name.lowercase()).append(": ")
                    append(message.content.replace(Regex("\\s+"), " ").take(400)).append('\n')
                }
            }.trim(),
        )
    }
}

/** Deliberate note-taking: the agent decides what deserves to persist. */
class MemoryWriteTool(
    private val memoryRepository: MemoryRepository,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "memory_write",
        description = "Keep a durable note in long-term memory: stable preferences, ongoing " +
            "projects, agreements, important dates, corrections. Never small talk or one-off " +
            "details, never secrets. Pass replaces=<note id> when a stored note is wrong or outdated.",
        parametersJson = """
            {"type":"object","properties":{
              "content":{"type":"string","description":"the durable fact, one line, in the user's language"},
              "kind":{"type":"string","enum":["profile","preference","event","plan","agreement","fact"],"description":"default fact"},
              "importance":{"type":"integer","description":"1-5, default 3"},
              "when":{"type":"string","description":"the date this refers to, if any, like 2026-09-12"},
              "source":{"type":"string","enum":["user","assistant","external"],"description":"who the fact comes from; default user (what the user said themselves)"},
              "entities":{"type":"array","items":{"type":"string"},"description":"people/projects/places this is about, exact names, at most 6"},
              "replaces":{"type":"integer","description":"id of an existing note this one supersedes"}
            },"required":["content"]}
        """.trimIndent(),
        alwaysAvailable = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val content = arguments.string("content")?.trim().orEmpty()
        if (content.isEmpty()) return ToolResult.Failure("Missing 'content' argument")
        if (content.length > MAX_CONTENT_CHARS) {
            return ToolResult.Failure(
                "Note is too long (${content.length} chars, max $MAX_CONTENT_CHARS). Split it or shorten it.",
            )
        }
        val kind = NoteKind.fromWire(arguments.string("kind"))
        val importance = (arguments.int("importance") ?: 3).coerceIn(1, 5)
        val whenRaw = arguments.string("when")?.trim()?.takeIf { it.isNotEmpty() }
        val whenAt = whenRaw?.let(MemoryText::parseWhen)
        val replaces = arguments.long("replaces")?.takeIf { it > 0 }
        val sourceRaw = arguments.string("source")?.trim()?.takeIf { it.isNotEmpty() }
        val source = if (sourceRaw == null) NoteSource.USER else NoteSource.fromWire(sourceRaw)
        val entities = (arguments["entities"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_ENTITIES)

        return when (val outcome = memoryRepository.addNote(
            kind = kind,
            content = content,
            importance = importance,
            conversationId = context.run.conversationId,
            whenAt = whenAt,
            source = source,
            entities = entities,
        )) {
            is MemoryRepository.AddOutcome.Duplicate ->
                ToolResult.Success(
                    "Already known as note #${outcome.id}; not saved again. " +
                        "Use memory_write with replaces=${outcome.id} if this corrects it.",
                )

            is MemoryRepository.AddOutcome.Suppressed ->
                ToolResult.Success(
                    "Refused: this was explicitly forgotten before (note #${outcome.id}). " +
                        "Confirm with the user before re-learning it.",
                )

            is MemoryRepository.AddOutcome.Merged -> {
                val superseded = supersedeIfRequested(replaces, outcome.id)
                ToolResult.Success(
                    buildString {
                        append("Reconsolidated into note #").append(outcome.id)
                            .append(" (near-duplicate updated, strength reinforced)")
                        if (superseded != null) append(". Superseded note #").append(superseded)
                        append('.')
                    },
                )
            }

            is MemoryRepository.AddOutcome.Saved -> {
                val superseded = supersedeIfRequested(replaces, outcome.id)
                ToolResult.Success(
                    buildString {
                        append("Saved note #").append(outcome.id)
                            .append(" (").append(kind.wire())
                            .append(if (source != NoteSource.USER) ", ${source.wire()}" else "")
                            .append(", importance ").append(importance).append(')')
                        if (whenAt != null) append(" for ").append(MemoryText.dateOnly(whenAt))
                        if (whenRaw != null && whenAt == null) {
                            append(" (could not parse when=\"").append(whenRaw).append("\"; ignored)")
                        }
                        if (superseded != null) append(". Superseded note #").append(superseded)
                        if (outcome.similarIds.isNotEmpty()) {
                            append(". Similar note #").append(outcome.similarIds.joinToString("#"))
                                .append(" already exists - resend with replaces= if this supersedes it")
                        }
                        append('.')
                    },
                )
            }
        }
    }

    private suspend fun supersedeIfRequested(replaces: Long?, newId: Long): Long? {
        if (replaces == null || replaces == newId) return null
        if (memoryRepository.noteById(replaces) == null) return null
        memoryRepository.archive(replaces, supersededBy = newId)
        return replaces
    }

    private companion object {
        const val MAX_CONTENT_CHARS = 400
        const val MAX_ENTITIES = 6
    }
}

/** Active suppression: the user asked to forget, the agent archives the trace. */
class MemoryForgetTool(
    private val memoryRepository: MemoryRepository,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "memory_forget",
        description = "Archive memories the user asked to forget (soft delete; raw conversations " +
            "are never touched). Find notes by id from memory_search, or by query. Confirm what " +
            "will be forgotten with the user before calling this.",
        parametersJson = """
            {"type":"object","properties":{
              "ids":{"type":"array","items":{"type":"integer"},"description":"note ids to forget"},
              "query":{"type":"string","description":"keywords to find notes when ids are unknown"},
              "reason":{"type":"string","description":"short reason, for the record"}
            }}
        """.trimIndent(),
        alwaysAvailable = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val ids = (arguments["ids"] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.longOrNull?.takeIf { value -> value > 0 } }
            .take(MAX_FORGET)
        val query = arguments.string("query")?.trim().orEmpty()
        if (ids.isEmpty() && query.isEmpty()) {
            return ToolResult.Failure("Provide 'ids' or 'query'")
        }
        val targets = LinkedHashMap<Long, Note>()
        ids.forEach { id -> memoryRepository.noteById(id)?.let { targets[it.id] = it } }
        if (targets.isEmpty() && query.isNotEmpty()) {
            memoryRepository.recall(
                terms = MemoryText.terms(query),
                limit = 3,
                spread = false,
                feedback = false,
            ).forEach { scored -> targets[scored.note.id] = scored.note }
        }
        if (targets.isEmpty()) return ToolResult.Success("No matching notes to forget.")
        targets.values.forEach { memoryRepository.suppress(it.id) }
        return ToolResult.Success(
            buildString {
                append("Forgotten ").append(targets.size)
                    .append(" note(s); re-learning is blocked until the user confirms:\n")
                targets.values.forEach { note ->
                    append("- #").append(note.id).append(' ').append(note.content.replace('\n', ' '))
                        .append('\n')
                }
            }.trim(),
        )
    }

    private companion object {
        const val MAX_FORGET = 10
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.long(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull
