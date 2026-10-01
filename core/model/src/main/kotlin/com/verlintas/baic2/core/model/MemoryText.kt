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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pure text helpers for recall: cue extraction, dedupe normalisation,
 * snippet building and human time phrasing. Keeping them here makes the
 * "hippocampus" testable without Android or a database.
 */
object MemoryText {

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Letters and digits only, lowercased: the canonical form for dedupe. */
    fun normalize(text: String): String = buildString(text.length) {
        text.forEach { character ->
            when {
                character.isLetterOrDigit() -> append(character.lowercaseChar())
                else -> Unit
            }
        }
    }

    /**
     * Cue extraction. Latin words survive as-is (3+ chars); CJK/Hangul runs
     * become bigrams so that "咖啡店" can still wake a note that says
     * "新开的咖啡店" without any tokeniser.
     */
    fun terms(query: String, maxTerms: Int = 12): List<String> {
        val tokens = query.split(Regex("[\\s\\p{P}\\p{S}]+"))
        val terms = LinkedHashSet<String>()
        for (token in tokens) {
            if (token.isBlank()) continue
            if (token.any { isCjk(it) }) {
                val chars = token.toList()
                if (chars.size <= 2) {
                    terms += token
                } else {
                    for (index in 0 until chars.size - 1) {
                        terms += "${chars[index]}${chars[index + 1]}"
                    }
                }
            } else {
                val word = token.lowercase(Locale.ROOT)
                if (word.length >= 3) terms += word
            }
            if (terms.size >= maxTerms) break
        }
        return terms.take(maxTerms)
    }

    /** A compact fragment around the first cue hit, ellipsised on both sides. */
    fun snippet(content: String, terms: List<String>, window: Int = 160): String {
        val flat = content.replace(Regex("\\s+"), " ").trim()
        if (flat.isEmpty()) return ""
        if (flat.length <= window) return flat
        val lower = flat.lowercase(Locale.ROOT)
        val hit = terms.asSequence()
            .map { term -> lower.indexOf(term.lowercase(Locale.ROOT)) }
            .filter { it >= 0 }
            .minOrNull()
        val anchor = hit ?: 0
        var start = (anchor - window / 3).coerceIn(0, flat.length)
        var end = (start + window).coerceAtMost(flat.length)
        start = safeBoundary(flat, start, forward = true)
        end = safeBoundary(flat, end, forward = false)
        return buildString {
            if (start > 0) append('…')
            append(flat.substring(start, end).trim())
            if (end < flat.length) append('…')
        }
    }

    /** "3 days ago" style phrasing; falls back to a date for old memories. */
    fun relativeTime(now: Long, then: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val delta = (now - then).coerceAtLeast(0L)
        return when {
            delta < 60_000L -> "just now"
            delta < 3_600_000L -> "${delta / 60_000L} min ago"
            delta < 86_400_000L -> "${delta / 3_600_000L} h ago"
            delta < 2 * 86_400_000L -> "yesterday"
            delta < 7 * 86_400_000L -> "${delta / 86_400_000L} days ago"
            delta < 30 * 86_400_000L -> "${delta / (7 * 86_400_000L)} weeks ago"
            else -> "on " + dateOnly(then, zone)
        }
    }

    fun formatDateTime(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_TIME.format(Instant.ofEpochMilli(epoch).atZone(zone))

    fun dateOnly(epoch: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_ONLY.format(Instant.ofEpochMilli(epoch).atZone(zone))

    /** Lenient ISO parsing for the `when` field the model supplies. */
    fun parseWhen(raw: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text).atStartOfDay(zone).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDateTime.parse(text.replace(' ', 'T')).atZone(zone).toInstant().toEpochMilli()
            }
            .recoverCatching { Instant.parse(text).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .getOrNull()
    }

    /**
     * Pattern-completion similarity for reconsolidation: 1.0 for the same
     * normalised text, falling off with edit distance. Cheap length gate first,
     * so a write against a few hundred short notes stays fast.
     */
    fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val maxLength = maxOf(a.length, b.length)
        if (minOf(a.length, b.length).toDouble() / maxLength < 0.5) return 0.0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return 1.0 - previous[b.length].toDouble() / maxLength
    }

    private fun isCjk(character: Char): Boolean {
        val code = character.code
        return code in 0x3040..0x30FF || // kana
            code in 0x3400..0x4DBF || // CJK ext A
            code in 0x4E00..0x9FFF || // CJK unified
            code in 0xF900..0xFAFF || // compatibility ideographs
            code in 0xAC00..0xD7AF // hangul syllables
    }

    private fun safeBoundary(text: String, index: Int, forward: Boolean): Int {
        var boundary = index.coerceIn(0, text.length)
        if (forward) {
            while (boundary > 0 && boundary < text.length &&
                text[boundary].isLowSurrogate() && text[boundary - 1].isHighSurrogate()
            ) {
                boundary--
            }
        } else {
            while (boundary > 0 && boundary < text.length &&
                text[boundary - 1].isHighSurrogate() && text[boundary].isLowSurrogate()
            ) {
                boundary++
            }
        }
        return boundary
    }
}
