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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownNormalizerTest {

    @Test
    fun convertsFullWidthSyntaxCharacters() {
        val normalized = MarkdownNormalizer.normalize("名称｜价格\n＊重点＊")
        assertTrue(normalized.contains('|'))
        assertTrue(normalized.contains('*'))
        assertFalse(normalized.contains('\uFF5C'))
        assertFalse(normalized.contains('\uFF0A'))
    }

    @Test
    fun insertsSpacesAfterSyntaxMarkers() {
        val normalized = MarkdownNormalizer.normalize("###标题\n-项目\n1.步骤\n>引用")
        assertEquals("### 标题\n- 项目\n1. 步骤\n> 引用", normalized)
    }

    @Test
    fun keepsWellFormedMarkdownUnchanged() {
        val source = "## Heading\n- item\n1. step\n> quote"
        assertEquals(source, MarkdownNormalizer.normalize(source))
    }

    @Test
    fun parsesPipeTablesIntoTableBlocks() {
        val text = """
            | 名称 | 价格 |
            | --- | --- |
            | 苹果 | 3 |
            | 香蕉 | 5 |
        """.trimIndent()

        val blocks = parseMarkdownBlocks(text)

        assertEquals(1, blocks.size)
        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("名称", "价格"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("苹果", "3"), table.rows[0])
    }

    @Test
    fun parsesPipeLessTables() {
        val text = "name | price\n--- | ---\napple | 3"
        val blocks = parseMarkdownBlocks(text)
        assertEquals(1, blocks.size)
        val table = blocks[0] as MarkdownBlock.Table
        assertEquals(listOf("name", "price"), table.header)
        assertEquals(listOf("apple", "3"), table.rows[0])
    }

    @Test
    fun plainPipesWithoutSeparatorStayParagraph() {
        val blocks = parseMarkdownBlocks("a | b | c")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
    }

    @Test
    fun separatorDetectionRejectsTextLines() {
        assertTrue(MarkdownNormalizer.isTableSeparator("| --- | :---: |"))
        assertTrue(MarkdownNormalizer.isTableSeparator("--- | ---"))
        assertFalse(MarkdownNormalizer.isTableSeparator("hello | world"))
        assertFalse(MarkdownNormalizer.isTableSeparator("- item"))
    }
}
