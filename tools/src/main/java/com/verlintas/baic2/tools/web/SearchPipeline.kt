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

import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Pure merge/rank/extract logic for the multi-engine web search: URL
 * normalization with tracking-param stripping, dedup (URL + near-duplicate
 * titles + per-domain cap), relevance ranking and article-body extraction.
 */
internal object SearchPipeline {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "spm", "from", "ref", "ref_src", "source", "fbclid", "gclid", "igshid",
        "mc_cid", "mc_eid", "vd_source",
    )

    fun normalizeUrl(raw: String): String {
        val url = raw.toHttpUrlOrNull() ?: return raw
        val builder = url.newBuilder().fragment(null)
        TRACKING_PARAMS.forEach { builder.removeAllQueryParameters(it) }
        var normalized = builder.build().toString()
        while (normalized.endsWith("?") || normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    fun domainOf(url: String): String =
        (url.toHttpUrlOrNull()?.host ?: url).removePrefix("www.")

    fun normalizedTitle(title: String): String =
        title.lowercase().replace(Regex("[\\p{Punct}\\s]+"), "")

    fun titleSimilar(a: String, b: String): Boolean {
        val x = normalizedTitle(a)
        val y = normalizedTitle(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y || x.contains(y) || y.contains(x)) return true
        val bigramsX = x.windowed(2).toHashSet()
        val bigramsY = y.windowed(2).toHashSet()
        if (bigramsX.isEmpty() || bigramsY.isEmpty()) return false
        val intersection = bigramsX.count { it in bigramsY }
        val union = bigramsX.size + bigramsY.size - intersection
        return union > 0 && intersection.toFloat() / union >= 0.8f
    }

    /** URL dedup, near-duplicate title dedup, [maxPerDomain] hits per domain. */
    fun dedupe(hits: List<SearchHit>, maxPerDomain: Int = 3): List<SearchHit> {
        val seenUrls = HashSet<String>()
        val kept = ArrayList<SearchHit>(hits.size)
        val perDomain = HashMap<String, Int>()
        for (hit in hits) {
            val url = normalizeUrl(hit.url)
            if (!seenUrls.add(url)) continue
            val domain = domainOf(url)
            val count = perDomain[domain] ?: 0
            if (count >= maxPerDomain) continue
            if (kept.any { titleSimilar(it.title, hit.title) }) continue
            perDomain[domain] = count + 1
            kept += hit.copy(url = url)
        }
        return kept
    }

    /** Query terms: words for Latin text, character bigrams for CJK runs. */
    fun terms(query: String): List<String> {
        val result = LinkedHashSet<String>()
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            if (word.any { it.code > 0x2E7F }) {
                if (word.length == 1) result += word else word.windowed(2).forEach { result += it }
            } else if (word.length > 1) {
                result += word
            }
        }
        return result.toList()
    }

    /** Title matches weigh 3×, snippet matches 1×; engine order breaks ties. */
    fun rank(hits: List<SearchHit>, queries: List<String>): List<SearchHit> {
        val allTerms = queries.flatMap(::terms).distinct()
        if (allTerms.isEmpty()) return hits
        return hits.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<SearchHit>> { score(it.value, allTerms) }
                    .thenBy { it.index },
            )
            .map { it.value }
    }

    private fun score(hit: SearchHit, terms: List<String>): Int {
        val title = hit.title.lowercase()
        val snippet = hit.snippet.lowercase()
        var total = 0
        terms.forEach { term ->
            if (title.contains(term)) total += 3
            if (snippet.contains(term)) total += 1
        }
        return total
    }

    private val NOISE = listOf(
        "点击查看", "扫码", "二维码", "红包", "关注我们", "相关阅读", "推荐阅读", "阅读全文",
        "登录", "注册", "版权", "广告", "免责声明", "更多精彩", "微信", "微博",
    )

    /** Element-level article extraction with noise and duplicate filtering. */
    fun extractArticle(html: String): String {
        val doc: Document = Jsoup.parse(html)
        doc.select("script, style, nav, header, footer, aside, noscript, iframe, svg, form").remove()
        val container = doc.selectFirst("article")
            ?: doc.selectFirst("main")
            ?: doc.selectFirst("[role=main]")
            ?: doc.body()
            ?: return ""
        val lines = ArrayList<String>()
        container.select("h1, h2, h3, p, li, pre, blockquote").forEach { element ->
            val text = element.text().replace(Regex("\\s+"), " ").trim()
            val heading = element.tagName() in setOf("h1", "h2", "h3")
            if (text.length < 12 && !heading) return@forEach
            if (!heading && text.length < 60 && NOISE.any { text.contains(it) }) return@forEach
            if (lines.lastOrNull() == text) return@forEach
            lines += text
        }
        return lines.joinToString("\n").trim()
    }
}

/** Five-minute TTL cache so follow-up searches don't refetch. */
internal object SearchCache {

    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_ENTRIES = 32

    private val entries = ConcurrentHashMap<String, Pair<Long, String>>()

    fun get(key: String): String? {
        val entry = entries[key] ?: return null
        if (System.currentTimeMillis() - entry.first > TTL_MS) {
            entries.remove(key)
            return null
        }
        return entry.second
    }

    fun put(key: String, value: String) {
        if (entries.size >= MAX_ENTRIES) {
            entries.entries.minByOrNull { it.value.first }?.let { entries.remove(it.key) }
        }
        entries[key] = System.currentTimeMillis() to value
    }
}
