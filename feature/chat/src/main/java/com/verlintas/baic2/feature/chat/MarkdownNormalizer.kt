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

package com.verlintas.baic2.feature.chat

/**
 * Models emit markdown that strict parsers reject. Repairs are deliberately
 * narrow and lossless: they only touch syntax characters, never words.
 *  - full-width pipes / stars used by CJK keyboards
 *  - missing spaces after `#`, `-`, `*`, `>`, `1.` (e.g. `###标题`)
 *  - `|` tables without outer pipes
 */
object MarkdownNormalizer {

    private val HEADING_SPACE = Regex("^(#{1,6})(?![#\\s])(?=\\S)", RegexOption.MULTILINE)
    private val BULLET_SPACE = Regex("^(\\s*[-•])(?=[^\\s\\-*•\\d])(?=\\S)", RegexOption.MULTILINE)
    private val NUMBERED_SPACE = Regex("^(\\s*\\d+[.)])(?=[^\\s\\d])", RegexOption.MULTILINE)
    private val QUOTE_SPACE = Regex("^(>+)(?=\\S)", RegexOption.MULTILINE)

    fun normalize(text: String): String {
        var result = text
            .replace('\uFF5C', '|')   // ｜ -> |
            .replace('\uFF0A', '*')   // ＊ -> *
            .replace("\r\n", "\n")
        result = HEADING_SPACE.replace(result) { "${it.groupValues[1]} " }
        result = BULLET_SPACE.replace(result) { "${it.groupValues[1]} " }
        result = NUMBERED_SPACE.replace(result) { "${it.groupValues[1]} " }
        result = QUOTE_SPACE.replace(result) { "${it.groupValues[1]} " }
        return result
    }

    /**
     * Splits a table row into cells. Handles rows with or without the outer
     * pipes (`a | b` and `| a | b |`); an empty trailing cell from a trailing
     * pipe is dropped.
     */
    fun splitTableRow(line: String): List<String> {
        val trimmed = line.trim()
        val withoutEdges = trimmed
            .removePrefix("|")
            .removeSuffix("|")
        return withoutEdges.split('|').map { it.trim() }
    }

    private val SEPARATOR_CELL = Regex("^:?-{2,}:?$")

    /** True when the line looks like a table separator (|---|---|). */
    fun isTableSeparator(line: String): Boolean {
        if ('-' !in line) return false
        val cells = splitTableRow(line)
        return cells.isNotEmpty() && cells.all { SEPARATOR_CELL.matches(it) }
    }

    /** True when the line looks like a table row with at least one pipe. */
    fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.contains('|') && !trimmed.startsWith("```")
    }

    /** Column alignment from the separator row (`:---`, `:---:`, `---:`). */
    fun tableAlignments(separatorLine: String): List<MarkdownNormalizer.TableAlignment> =
        splitTableRow(separatorLine).map { cell ->
            val left = cell.startsWith(":")
            val right = cell.endsWith(":")
            when {
                left && right -> TableAlignment.CENTER
                right -> TableAlignment.RIGHT
                else -> TableAlignment.LEFT
            }
        }

    enum class TableAlignment { LEFT, CENTER, RIGHT }
}
