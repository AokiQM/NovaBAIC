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

import kotlinx.serialization.Serializable

/**
 * The kinds of durable notes the agent can keep, mirroring how memory is
 * usually classified: identity (profile), stable attitudes (preference),
 * time-anchored happenings (event), intentions (plan), commitments
 * (agreement), plain durable facts (fact) and compressed history (summary).
 */
@Serializable
enum class NoteKind {
    FACT,
    PROFILE,
    PREFERENCE,
    EVENT,
    PLAN,
    AGREEMENT,
    SUMMARY,
    ;

    /** The lowercase name used on the tool/prompt wire. */
    fun wire(): String = name.lowercase()

    companion object {
        val wireNames: List<String> = NoteKind.entries.filter { it != SUMMARY }.map { it.wire() }

        fun fromWire(raw: String?): NoteKind = when (raw?.trim()?.lowercase()) {
            "profile" -> PROFILE
            "preference" -> PREFERENCE
            "event" -> EVENT
            "plan" -> PLAN
            "agreement" -> AGREEMENT
            "summary" -> SUMMARY
            else -> FACT
        }
    }
}

/**
 * One durable note. Notes point back at their source ([conversationId],
 * [messageId]) so the model can always drill into the original episode, and
 * they can supersede each other instead of silently contradicting.
 */
@Serializable
data class Note(
    val id: Long = 0L,
    val kind: NoteKind = NoteKind.FACT,
    val content: String,
    val importance: Int = 3,
    val pinned: Boolean = false,
    val conversationId: Long? = null,
    val messageId: Long? = null,
    val whenAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastAccessedAt: Long = 0L,
    val accessCount: Int = 0,
    /** Synaptic strength: retrieval reconsolidates and slows the decay down. */
    val strength: Double = 1.0,
    val supersededBy: Long? = null,
    val archived: Boolean = false,
)

/**
 * The always-on core memory, kept in the context window at a fixed small
 * budget: who the user is, and what is going on right now. This is what keeps
 * the agent's sense of continuity stable while everything else is recalled on
 * demand.
 */
@Serializable
data class CoreMemory(
    val user: String = "",
    val context: String = "",
    val updatedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = user.isBlank() && context.isBlank()
}

/** One synthetic hit of episodic recall: an original message, with a handle. */
data class MessageHit(
    val messageId: Long,
    val conversationId: Long,
    val conversationTitle: String,
    val role: ChatRole,
    val content: String,
    val createdAt: Long,
)
