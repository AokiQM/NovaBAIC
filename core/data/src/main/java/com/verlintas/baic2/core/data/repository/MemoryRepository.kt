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

package com.verlintas.baic2.core.data.repository

import androidx.room.withTransaction
import com.verlintas.baic2.core.data.db.Baic2Database
import com.verlintas.baic2.core.data.db.CoreMemoryEntity
import com.verlintas.baic2.core.data.db.NoteEntity
import com.verlintas.baic2.core.data.db.NoteLinkEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.CoreMemory
import com.verlintas.baic2.core.model.MemoryScoring
import com.verlintas.baic2.core.model.MemoryText
import com.verlintas.baic2.core.model.Note
import com.verlintas.baic2.core.model.NoteKind
import com.verlintas.baic2.core.model.ScoredNote
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The curated (semantic) memory: durable notes the agent and the user write,
 * plus the always-on core blocks. Raw episodes live in the message table;
 * this layer only ever stores what someone chose to keep.
 *
 * Retrieval is deliberately biomimetic: recalling notes reactivates them
 * (strength, use counts) and wires the co-recalled set together (Hebbian
 * links), and recall itself can spread one hop along those links.
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val db: Baic2Database,
    private val mapper: ChatMapper,
) {

    sealed interface AddOutcome {
        /**
         * Stored as new. [similarIds] carries near-duplicates already on file
         * so the caller can tell the model to supersede them if needed.
         */
        data class Saved(val id: Long, val similarIds: List<Long> = emptyList()) : AddOutcome

        /** Exact-normalised duplicate: nothing was written. */
        data class Duplicate(val id: Long) : AddOutcome

        /** Near-duplicate reconsolidated into the existing trace. */
        data class Merged(val id: Long) : AddOutcome
    }

    fun observeActive(limit: Int = 500): Flow<List<Note>> =
        db.noteDao().observeActive(limit).map { list -> list.map(mapper::noteToModel) }

    fun observeCore(): Flow<CoreMemory> =
        db.coreMemoryDao().observeAll().map(mapper::coreToModel)

    suspend fun getCore(): CoreMemory = mapper.coreToModel(db.coreMemoryDao().getAll())

    /** Only non-null slots are written; the other block keeps its content. */
    suspend fun setCore(user: String? = null, context: String? = null) {
        val now = System.currentTimeMillis()
        user?.let {
            db.coreMemoryDao().upsert(CoreMemoryEntity(CoreMemoryEntity.SLOT_USER, it.trim(), now))
        }
        context?.let {
            db.coreMemoryDao().upsert(CoreMemoryEntity(CoreMemoryEntity.SLOT_CONTEXT, it.trim(), now))
        }
    }

    suspend fun listActive(limit: Int = 300): List<Note> =
        db.noteDao().getActive(limit).map(mapper::noteToModel)

    /**
     * Pattern completion on write: the same fact is never stored twice, a very
     * close variant is reconsolidated into the existing trace, and a merely
     * similar one is stored while pointing at what it may supersede.
     */
    suspend fun addNote(
        kind: NoteKind,
        content: String,
        importance: Int = 3,
        conversationId: Long? = null,
        messageId: Long? = null,
        whenAt: Long? = null,
    ): AddOutcome {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return AddOutcome.Duplicate(0L)
        val active = db.noteDao().getActive(PAGE_SIZE).map(mapper::noteToModel)
        var best: Note? = null
        var bestSimilarity = 0.0
        active.forEach { existing ->
            val similarity = MemoryText.similarity(trimmed, existing.content)
            if (similarity > bestSimilarity) {
                best = existing
                bestSimilarity = similarity
            }
        }
        val now = System.currentTimeMillis()
        when {
            best != null && bestSimilarity >= 1.0 -> return AddOutcome.Duplicate(best.id)

            best != null && bestSimilarity >= MERGE_SIMILARITY -> {
                db.noteDao().update(
                    id = best.id,
                    content = trimmed,
                    importance = maxOf(best.importance, importance.coerceIn(1, 5)),
                    now = now,
                )
                db.noteDao().touch(best.id, now)
                return AddOutcome.Merged(best.id)
            }

            else -> {
                val id = db.noteDao().insert(
                    NoteEntity(
                        kind = kind.name,
                        content = trimmed,
                        importance = importance.coerceIn(1, 5),
                        pinned = false,
                        conversationId = conversationId,
                        messageId = messageId,
                        whenAt = whenAt,
                        createdAt = now,
                        updatedAt = now,
                        lastAccessedAt = 0L,
                        accessCount = 0,
                        strength = 1.0,
                        supersededBy = null,
                        archived = false,
                    ),
                )
                val similarIds = if (best != null && bestSimilarity >= HINT_SIMILARITY) {
                    listOf(best.id)
                } else {
                    emptyList()
                }
                return AddOutcome.Saved(id, similarIds)
            }
        }
    }

    suspend fun updateNote(id: Long, content: String, importance: Int) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        db.noteDao().update(id, trimmed, importance.coerceIn(1, 5), System.currentTimeMillis())
    }

    suspend fun setPinned(id: Long, pinned: Boolean) =
        db.noteDao().setPinned(id, pinned, System.currentTimeMillis())

    /** Soft forget: the note leaves recall, its associations are pruned. */
    suspend fun archive(id: Long, supersededBy: Long? = null) = db.withTransaction {
        db.noteDao().archive(id, supersededBy, System.currentTimeMillis())
        db.noteLinkDao().deleteFor(id)
    }

    suspend fun delete(id: Long) = db.withTransaction {
        db.noteDao().delete(id)
        db.noteLinkDao().deleteFor(id)
    }

    /** Retrieval strengthens a memory, like it does in a brain. */
    suspend fun touch(ids: Collection<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { id -> db.noteDao().touch(id, now) }
    }

    /**
     * Prospective memory: plans and events whose time is near (or recently
     * past) surface on their own, like a human remembering an appointment.
     */
    suspend fun upcoming(
        now: Long = System.currentTimeMillis(),
        windowMs: Long = 7 * 86_400_000L,
        overdueMs: Long = 30 * 86_400_000L,
        limit: Int = 3,
    ): List<Note> {
        val from = now - overdueMs
        val to = now + windowMs
        val result = db.noteDao().getActive(PAGE_SIZE)
            .map(mapper::noteToModel)
            .filter { note ->
                (note.kind == NoteKind.PLAN || note.kind == NoteKind.EVENT) &&
                    note.whenAt != null && note.whenAt in from..to
            }
            .sortedBy { it.whenAt }
            .take(limit)
        // Surfacing a prospective memory is itself a retrieval: reinforce it.
        if (result.isNotEmpty()) touch(result.map { it.id })
        return result
    }

    /**
     * Cue-driven recall. When [spread] is on, the top cue hits act as seeds and
     * light up their associates one hop along the Hebbian links. Every call is
     * a real retrieval event: returned notes are reconsolidated ([touch]) and
     * wired together ([reinforceLinks]) unless [feedback] is off.
     */
    suspend fun recall(
        terms: List<String>,
        limit: Int = 8,
        offset: Int = 0,
        minHits: Int = 1,
        minScore: Double = 0.0,
        excludeKinds: Set<NoteKind> = emptySet(),
        spread: Boolean = true,
        feedback: Boolean = true,
    ): List<ScoredNote> {
        val notes = db.noteDao().getActive(PAGE_SIZE)
            .map(mapper::noteToModel)
            .filter { it.kind !in excludeKinds }
        if (notes.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val ranked = MemoryScoring.rank(notes, terms, now)
            .filter { it.hits >= minHits && it.score >= minScore }
        val page = ranked.drop(offset).take(limit)
        if (page.isEmpty()) return emptyList()

        val result = if (spread) {
            val seeds = page.take(SPREAD_SEEDS).filter { it.hits > 0 || terms.isEmpty() }
            spreadActivation(seeds, page.map { it.note.id }.toSet(), notes, excludeKinds)
                ?.let { awakened ->
                    (page + awakened.filter { it.score >= minScore })
                        .distinctBy { it.note.id }
                        .sortedByDescending { it.score }
                        .take(limit)
                } ?: page
        } else {
            page
        }
        if (feedback) {
            val ids = result.map { it.note.id }
            touch(ids)
            reinforceLinks(ids)
        }
        return result
    }

    /** Fire together, wire together: co-recalled notes gain link weight. */
    private suspend fun reinforceLinks(ids: List<Long>) {
        if (ids.size < 2) return
        val now = System.currentTimeMillis()
        db.withTransaction {
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val a = minOf(ids[i], ids[j])
                    val b = maxOf(ids[i], ids[j])
                    val existing = db.noteLinkDao().get(a, b)
                    if (existing == null) {
                        db.noteLinkDao().insert(NoteLinkEntity(a, b, 1f, now))
                    } else if (existing.weight < MAX_LINK_WEIGHT) {
                        db.noteLinkDao().updateWeight(
                            a,
                            b,
                            (existing.weight + 1f).coerceAtMost(MAX_LINK_WEIGHT),
                            now,
                        )
                    }
                }
            }
        }
    }

    /** One hop of spreading activation from the seeds. */
    private suspend fun spreadActivation(
        seeds: List<ScoredNote>,
        already: Set<Long>,
        active: List<Note>,
        excludeKinds: Set<NoteKind>,
    ): List<ScoredNote>? {
        if (seeds.isEmpty()) return null
        val seedScores = seeds.associate { it.note.id to it.score }
        val links = db.noteLinkDao().linksFor(seedScores.keys.toList())
        if (links.isEmpty()) return null
        val byId = active.associateBy { it.id }
        val boosted = mutableMapOf<Long, ScoredNote>()
        links.forEach { link ->
            val sourceId = when {
                link.a in seedScores -> link.a
                link.b in seedScores -> link.b
                else -> return@forEach
            }
            val source = seedScores.getValue(sourceId)
            val otherId = if (sourceId == link.a) link.b else link.a
            if (otherId in already) return@forEach
            val note = byId[otherId] ?: return@forEach
            if (note.kind in excludeKinds) return@forEach
            val score = MemoryScoring.spreadScore(source, link.weight.toDouble())
            val current = boosted[otherId]
            if (current == null || score > current.score) {
                boosted[otherId] = ScoredNote(
                    note = note,
                    score = score,
                    hits = 0,
                    spread = true,
                    spreadFrom = sourceId,
                    linkWeight = link.weight.toDouble(),
                )
            }
        }
        return boosted.values.sortedByDescending { it.score }
    }

    suspend fun noteById(id: Long): Note? = db.noteDao().getById(id)?.let(mapper::noteToModel)

    suspend fun countActive(): Int = db.noteDao().countActive()

    private companion object {
        const val PAGE_SIZE = 500
        const val SPREAD_SEEDS = 3
        const val MAX_LINK_WEIGHT = 5f
        const val MERGE_SIMILARITY = 0.8
        const val HINT_SIMILARITY = 0.55
    }
}
