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

package com.verlintas.baic2.tools.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchPipelineTest {

    private fun hit(title: String, url: String, snippet: String = "") = SearchHit(title, url, snippet)

    @Test
    fun normalizeUrlStripsTrackingAndFragment() {
        val normalized = SearchPipeline.normalizeUrl(
            "https://example.com/post?utm_source=x&id=3&spm=abc#section",
        )
        assertEquals("https://example.com/post?id=3", normalized)
    }

    @Test
    fun dedupeRemovesUrlDuplicates() {
        val hits = listOf(
            hit("A", "https://a.com/x?utm_source=1"),
            hit("B", "https://a.com/x"),
        )
        assertEquals(1, SearchPipeline.dedupe(hits).size)
    }

    @Test
    fun dedupeCapsHitsPerDomain() {
        val hits = (1..5).map { hit("Title $it", "https://same.com/$it") }
        assertEquals(3, SearchPipeline.dedupe(hits).size)
    }

    @Test
    fun dedupeDropsNearDuplicateTitles() {
        val hits = listOf(
            hit("Android 16 release date announced", "https://a.com/1"),
            hit("Android 16 release date announced!", "https://b.com/2"),
        )
        assertEquals(1, SearchPipeline.dedupe(hits).size)
    }

    @Test
    fun rankPrefersTitleMatches() {
        val hits = listOf(
            hit("unrelated page", "https://a.com", "android 16 mentioned here"),
            hit("Android 16 review", "https://b.com", "hands-on"),
        )
        val ranked = SearchPipeline.rank(hits, listOf("android 16"))
        assertEquals("Android 16 review", ranked.first().title)
    }

    @Test
    fun termsUseBigramsForCjk() {
        val terms = SearchPipeline.terms("安卓 系统更新")
        assertTrue(terms.contains("安卓"))
        assertTrue(terms.contains("系统"))
        assertTrue(terms.contains("统更"))
        assertFalse(terms.contains("安卓 系统更新"))
    }

    @Test
    fun extractArticleKeepsBodyAndDropsNoise() {
        val html = """
            <html><body>
            <nav>menu</nav>
            <article>
              <h1>Title</h1>
              <p>First paragraph with enough text to keep.</p>
              <p>First paragraph with enough text to keep.</p>
              <p>扫码关注我们</p>
            </article>
            </body></html>
        """.trimIndent()
        val article = SearchPipeline.extractArticle(html)
        assertTrue(article.contains("Title"))
        assertTrue(article.contains("First paragraph"))
        assertFalse(article.contains("扫码"))
        assertEquals(1, article.lines().count { it.contains("First paragraph") })
    }

    @Test
    fun cacheStoresAndReadsValues() {
        SearchCache.put("k", "v")
        assertEquals("v", SearchCache.get("k"))
    }
}
