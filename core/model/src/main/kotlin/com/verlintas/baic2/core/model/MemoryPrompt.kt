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

/**
 * Renders memory into the two places it is allowed to cost tokens: the small
 * always-on block in the system prompt, and the curator's inventory view.
 */
object MemoryPrompt {

    private const val MAX_CONTEXT_CHARS = 2_400
    private const val MAX_INVENTORY_LINE = 240
    private const val MAX_INVENTORY_CHARS = 6_000

    /**
     * The always-on block: core memory plus the handful of notes primed by the
     * current message. Returns null when there is nothing worth saying.
     */
    fun context(core: CoreMemory?, primed: List<Note>, now: Long): String? {
        val parts = mutableListOf<String>()
        val user = core?.user?.trim().orEmpty()
        val ongoing = core?.context?.trim().orEmpty()
        if (user.isNotEmpty()) parts += "About the user:\n$user"
        if (ongoing.isNotEmpty()) parts += "Currently ongoing:\n$ongoing"
        if (primed.isNotEmpty()) {
            parts += "Notes primed by the current message:\n" + primed.joinToString("\n") { note ->
                "- (${note.kind.wire()}, ${MemoryText.dateOnly(note.whenAt ?: note.updatedAt)}) " +
                    note.content.replace('\n', ' ').trim()
            }
        }
        if (parts.isEmpty()) return null
        return "What you already remember:\n\n" +
            parts.joinToString("\n\n").take(MAX_CONTEXT_CHARS)
    }

    /** One line per note for the curator, ids included so it can revise them. */
    fun inventory(notes: List<Note>): String =
        notes.joinToString("\n") { note ->
            "#${note.id} ${note.kind.wire()} i${note.importance}" +
                (if (note.pinned) " pinned" else "") +
                " (${MemoryText.dateOnly(note.updatedAt)}): " +
                note.content.replace('\n', ' ').take(MAX_INVENTORY_LINE)
        }
            .take(MAX_INVENTORY_CHARS)
            .ifBlank { "(none)" }

    /** The curator sees the current core verbatim so it can preserve it. */
    fun coreBlocks(core: CoreMemory?): String = buildString {
        append("[user] ").append(core?.user?.trim().orEmpty().ifBlank { "(empty)" })
        append('\n')
        append("[context] ").append(core?.context?.trim().orEmpty().ifBlank { "(empty)" })
    }
}
