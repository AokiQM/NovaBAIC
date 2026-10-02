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
import com.verlintas.baic2.core.data.db.MemoryHoldEntity
import com.verlintas.baic2.core.data.db.NoteEntity
import com.verlintas.baic2.core.data.db.NoteLinkEntity
import com.verlintas.baic2.core.data.db.NoteRevisionEntity
import com.verlintas.baic2.core.data.mapper.ChatMapper
import com.verlintas.baic2.core.model.CoreMemory
import com.verlintas.baic2.core.model.MemoryHold
import com.verlintas.baic2.core.model.MemoryScoring
import com.verlintas.baic2.core.model.MemoryText
import com.verlintas.baic2.core.model.Note
import com.verlintas.baic2.core.model.NoteKind
import com.verlintas.baic2.core.model.NoteRevision
import com.verlintas.baic2.core.model.NoteSource
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
 * links), recall itself spreads along those links (two hops with decay),
 * entities give episodic "one thing at a time" access, decay prunes what is
 * never used, sleep-time rehearsal rescues what is fading but valuable, and an
 * explicitly forgotten trace leaves a suppression fingerprint that blocks
 * silent re-learning.
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

        /** The fact was explicitly forgotten before; re-learning is blocked. */
        data class Suppressed(val id: Long) : AddOutcome

        /** The user asked that this never be recorded; the hold blocks it. */
        data class Held(val holdId: Long) : AddOutcome
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
     * close variant is reconsolidated into the existing trace, a merely
     * similar one is stored while pointing at what it may supersede - and a
     * trace the user asked to forget blocks re-learning until they confirm.
     * Genuinely new facts encode a little stronger (novelty boost).
     */
    suspend fun addNote(
        kind: NoteKind,
        content: String,
        importance: Int = 3,
        conversationId: Long? = null,
        messageId: Long? = null,
        whenAt: Long? = null,
        source: NoteSource = NoteSource.USER,
        entities: List<String> = emptyList(),
    ): AddOutcome {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return AddOutcome.Duplicate(0L)
        // 0. Holds come first: a spoken promise ("don't record this") is state.
        val held = db.memoryHoldDao().getRecent(SUPPRESSED_SCAN)
            .firstOrNull { MemoryText.similarity(trimmed, it.content) >= SUPPRESSION_SIMILARITY }
        if (held != null) return AddOutcome.Held(held.id)
        // 1. Active suppression: forgetting is inhibition, not deletion.
        val suppressed = db.noteDao().getSuppressed(SUPPRESSED_SCAN)
            .firstOrNull { MemoryText.similarity(trimmed, it.content) >= SUPPRESSION_SIMILARITY }
        if (suppressed != null) return AddOutcome.Suppressed(suppressed.id)

        // 2. Pattern completion against the active notes.
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
                recordRevision(best)
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
                        strength = if (bestSimilarity < HINT_SIMILARITY) {
                            // Genuinely new: novelty boosts initial encoding.
                            NOVELTY_STRENGTH
                        } else {
                            1.0
                        },
                        source = source.name,
                        entities = MemoryText.encodeEntities(entities),
                        suppressed = false,
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

    /** [entities] replaces the links when provided; null keeps them. */
    suspend fun updateNote(
        id: Long,
        content: String,
        importance: Int,
        entities: List<String>? = null,
    ) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val existing = db.noteDao().getById(id) ?: return
        recordRevision(mapper.noteToModel(existing))
        val now = System.currentTimeMillis()
        if (entities == null) {
            db.noteDao().update(id, trimmed, importance.coerceIn(1, 5), now)
        } else {
            db.noteDao().updateWithEntities(
                id = id,
                content = trimmed,
                importance = importance.coerceIn(1, 5),
                entities = MemoryText.encodeEntities(entities).ifEmpty { existing.entities },
                now = now,
            )
        }
    }

    /**
     * Deterministic in-place replacement (the `replaces=<id>` path): the id
     * never changes, so references cannot dangle, and the old version is
     * archived into the revision history first.
     */
    suspend fun replaceNote(
        id: Long,
        content: String,
        importance: Int? = null,
        entities: List<String>? = null,
        kind: NoteKind? = null,
    ): Note? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null
        val existing = db.noteDao().getById(id) ?: return null
        val note = mapper.noteToModel(existing)
        recordRevision(note)
        val now = System.currentTimeMillis()
        db.noteDao().replaceInPlace(
            id = id,
            kind = (kind ?: note.kind).name,
            content = trimmed,
            importance = (importance ?: note.importance).coerceIn(1, 5),
            entities = entities?.let(MemoryText::encodeEntities).orEmpty().ifEmpty { existing.entities },
            now = now,
        )
        db.noteDao().touch(id, now)
        return db.noteDao().getById(id)?.let(mapper::noteToModel)
    }

    /** Old versions of a note, newest first. */
    suspend fun revisionsFor(noteId: Long, limit: Int = 10): List<NoteRevision> =
        db.noteRevisionDao().revisionsFor(noteId, limit).map { entity ->
            NoteRevision(
                id = entity.id,
                content = entity.content,
                importance = entity.importance,
                replacedAt = entity.replacedAt,
            )
        }

    private suspend fun recordRevision(note: Note) {
        db.noteRevisionDao().insert(
            NoteRevisionEntity(
                noteId = note.id,
                content = note.content,
                importance = note.importance,
                entities = MemoryText.encodeEntities(note.entities),
                replacedAt = System.currentTimeMillis(),
            ),
        )
        db.noteRevisionDao().prune(note.id, MAX_REVISIONS)
    }

    /** True hard delete: the row, its links and its history disappear. */
    suspend fun purge(id: Long) = db.withTransaction {
        db.noteDao().delete(id)
        db.noteLinkDao().deleteFor(id)
        db.noteRevisionDao().deleteFor(id)
    }

    /** A hold: "do not record this", stored as executable state. */
    suspend fun addHold(content: String, reason: String? = null): Long {
        val trimmed = content.trim()
        return db.memoryHoldDao().insert(
            MemoryHoldEntity(
                content = trimmed,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun listHolds(): List<MemoryHold> =
        db.memoryHoldDao().getRecent().map { entity ->
            MemoryHold(id = entity.id, content = entity.content, reason = entity.reason)
        }

    fun observeHolds(): Flow<List<MemoryHold>> =
        db.memoryHoldDao().observeAll().map { list ->
            list.map { entity -> MemoryHold(id = entity.id, content = entity.content, reason = entity.reason) }
        }

    suspend fun removeHold(id: Long) = db.memoryHoldDao().delete(id)

    /**
     * Closest notes by text similarity, for the "no confident match" exit:
     * never an empty answer, always something actionable.
     */
    suspend fun closestNotes(query: String, limit: Int = 3): List<Pair<Note, Double>> =
        db.noteDao().getActive(PAGE_SIZE)
            .asSequence()
            .map(mapper::noteToModel)
            .filter { it.kind != NoteKind.SUMMARY }
            .map { it to MemoryText.similarity(query, it.content) }
            .filter { it.second >= 0.2 }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()

    suspend fun setPinned(id: Long, pinned: Boolean) =
        db.noteDao().setPinned(id, pinned, System.currentTimeMillis())

    /** Curator-level forgetting: archived, but not suppressed (may be relearned). */
    suspend fun archive(id: Long, supersededBy: Long? = null) = db.withTransaction {
        db.noteDao().archive(id, supersededBy, System.currentTimeMillis())
        db.noteLinkDao().deleteFor(id)
    }

    /**
     * User-level forgetting: archive + suppression fingerprint. A later write
     * that matches this trace is refused until the user confirms.
     */
    suspend fun suppress(id: Long) = db.withTransaction {
        db.noteDao().suppress(id, System.currentTimeMillis())
        db.noteLinkDao().deleteFor(id)
    }

    suspend fun delete(id: Long) = suppress(id)

    /** Retrieval strengthens a memory, like it does in a brain. */
    suspend fun touch(ids: Collection<Long>) {
        val now = System.currentTimeMillis()
        ids.forEach { id -> db.noteDao().touch(id, now) }
    }

    /**
     * Synaptic pruning (sleep maintenance, no LLM): notes that are neither
     * important nor pinned, untouched for weeks and below the retrieval floor
     * are archived. Soft - the trace survives and can be revived with a strong cue.
     */
    suspend fun pruneStaleNotes(now: Long = System.currentTimeMillis()): Int {
        val candidates = db.noteDao().getActive(PAGE_SIZE)
            .map(mapper::noteToModel)
            .filter { it.isPrunable(now) }
        candidates.forEach { archive(it.id) }
        return candidates.size
    }

    private fun Note.isPrunable(now: Long): Boolean {
        if (pinned || importance > PRUNE_MAX_IMPORTANCE) return false
        val touched = maxOf(lastAccessedAt, updatedAt, createdAt)
        val ageDays = (now - touched).coerceAtLeast(0L) / 86_400_000.0
        return ageDays > PRUNE_MIN_AGE_DAYS &&
            MemoryScoring.retrievability(this, now) < PRUNE_RETRIEVABILITY
    }

    /**
     * Sleep rehearsal (curator feed): valuable traces that are fading and have
     * not been touched recently - candidates to revive, revise or let go.
     */
    suspend fun rehearsalCandidates(
        now: Long = System.currentTimeMillis(),
        limit: Int = 6,
    ): List<Note> = db.noteDao().getActive(PAGE_SIZE)
        .map(mapper::noteToModel)
        .filter { !it.pinned && it.kind != NoteKind.SUMMARY }
        .filter { it.importance >= REHEARSAL_MIN_IMPORTANCE || it.accessCount >= REHEARSAL_MIN_USES }
        .filter { now - maxOf(it.lastAccessedAt, it.updatedAt) > REHEARSAL_MIN_AGE_MS }
        .sortedBy { MemoryScoring.retrievability(it, now) }
        .filter { MemoryScoring.retrievability(it, now) < REHEARSAL_MAX_RETRIEVABILITY }
        .take(limit)

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
     * Entity recall ("tell me about 张伟"): the notes linked to a person,
     * project or place, ordered by importance/recency.
     */
    suspend fun recallByEntity(
        entities: List<String>,
        limit: Int = 6,
        feedback: Boolean = true,
    ): List<ScoredNote> {
        val names = entities.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(6)
            .toList()
        if (names.isEmpty()) return emptyList()
        val merged = LinkedHashMap<Long, Note>()
        names.forEach { entity ->
            db.noteDao().getByEntity(MemoryText.entityLikePattern(entity), ENTITY_PAGE)
                .forEach { entityRow -> merged[entityRow.id] = mapper.noteToModel(entityRow) }
        }
        if (merged.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val ranked = MemoryScoring.rank(merged.values.toList(), emptyList(), now).take(limit)
        if (feedback && ranked.isNotEmpty()) {
            val ids = ranked.map { it.note.id }
            touch(ids)
            reinforceLinks(ids)
        }
        return ranked
    }

    /** Distinct entity names known to memory, for context-dependent priming. */
    suspend fun knownEntities(): List<String> =
        db.noteDao().activeEntityBlobs()
            .asSequence()
            .flatMap { MemoryText.decodeEntities(it).asSequence() }
            .distinct()
            .toList()

    /**
     * Cue-driven recall. When [spread] is on, the top cue hits act as seeds and
     * light up their associates along the Hebbian links - one hop, then a
     * weaker second hop. Every call is a real retrieval event: returned notes
     * are reconsolidated ([touch]) and wired together ([reinforceLinks]).
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

    /** Spreading activation: one hop from the seeds, then a weaker hop two. */
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
                    spreadHops = 1,
                )
            }
        }
        if (boosted.isNotEmpty()) {
            val hopOne = boosted.values.sortedByDescending { it.score }.take(SPREAD_HOP2_SEEDS)
            val hopOneById = hopOne.associateBy { it.note.id }
            var secondHopAdded = 0
            val hopLinks = db.noteLinkDao().linksFor(hopOneById.keys.toList())
            hopLinks.forEach { link ->
                if (secondHopAdded >= SPREAD_HOP2_LIMIT) return@forEach
                val sourceId = when {
                    link.a in hopOneById -> link.a
                    link.b in hopOneById -> link.b
                    else -> return@forEach
                }
                val otherId = if (sourceId == link.a) link.b else link.a
                if (otherId in already || otherId in boosted) return@forEach
                val note = byId[otherId] ?: return@forEach
                if (note.kind in excludeKinds) return@forEach
                val source = hopOneById.getValue(sourceId)
                boosted[otherId] = ScoredNote(
                    note = note,
                    score = MemoryScoring.spreadScore(source.score, link.weight.toDouble()),
                    hits = 0,
                    spread = true,
                    spreadFrom = sourceId,
                    linkWeight = link.weight.toDouble(),
                    spreadHops = 2,
                )
                secondHopAdded++
            }
        }
        return boosted.values.sortedByDescending { it.score }
    }

    suspend fun noteById(id: Long): Note? = db.noteDao().getById(id)?.let(mapper::noteToModel)

    suspend fun countActive(): Int = db.noteDao().countActive()

    private companion object {
        const val PAGE_SIZE = 500
        const val SUPPRESSED_SCAN = 200
        const val ENTITY_PAGE = 60
        const val SPREAD_SEEDS = 3
        const val SPREAD_HOP2_SEEDS = 2
        const val SPREAD_HOP2_LIMIT = 2
        const val MAX_LINK_WEIGHT = 5f
        const val MERGE_SIMILARITY = 0.8
        const val HINT_SIMILARITY = 0.55
        const val SUPPRESSION_SIMILARITY = 0.7
        const val NOVELTY_STRENGTH = 1.3
        const val PRUNE_MAX_IMPORTANCE = 2
        const val PRUNE_MIN_AGE_DAYS = 45.0
        const val PRUNE_RETRIEVABILITY = 0.2
        const val REHEARSAL_MIN_IMPORTANCE = 4
        const val REHEARSAL_MIN_USES = 3
        const val REHEARSAL_MIN_AGE_MS = 3 * 86_400_000L
        const val REHEARSAL_MAX_RETRIEVABILITY = 0.5
        const val MAX_REVISIONS = 8
    }
}
