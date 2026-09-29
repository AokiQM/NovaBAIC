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

package com.verlintas.baic2.tools

import com.verlintas.baic2.device.api.TextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiMatchTest {

    private fun node(text: String, x: Int = 0, y: Int = 0) = TextNode(text, x, y, x + 10, y + 10)

    @Test
    fun scoresExactPrefixAndSubstringInOrder() {
        assertEquals(0, uiMatchScore("Settings", "settings"))
        assertEquals(2, uiMatchScore("Settings", "sett"))
        assertEquals(3, uiMatchScore("Network settings", "settings"))
    }

    @Test
    fun scoresTypoWithinTwoEdits() {
        assertEquals(5, uiMatchScore("Settings", "setings"))
        assertNull(uiMatchScore("Settings", "xyz"))
    }

    @Test
    fun scoresQueryContainingShortLabel() {
        assertEquals(4, uiMatchScore("Wi-Fi", "wi-fi settings"))
    }

    @Test
    fun bestMatchesSortsByScoreAndDeduplicates() {
        val matches = uiBestMatches(
            listOf(
                node("Advanced settings", 1, 1),
                node("Settings", 2, 2),
                node("Settings", 2, 2),
                node("Wi-fi", 3, 3),
            ),
            "settings",
        )
        assertEquals(2, matches.size)
        assertEquals("Settings", matches.first().text)
        assertTrue(matches.last().text.contains("Advanced"))
    }

    @Test
    fun noMatchForEmptyInput() {
        assertNull(uiMatchScore("Settings", "  "))
        assertTrue(uiBestMatches(listOf(node("a")), "").isEmpty())
    }
}
