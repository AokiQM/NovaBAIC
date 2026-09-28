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

import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import java.io.IOException
import java.net.URLDecoder
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

internal data class SearchHit(val title: String, val url: String, val snippet: String)

/** Shared, SSRF-guarded HTTP fetching for web tools. */
class WebFetcher @Inject constructor(
    client: OkHttpClient,
) {

    private val client = client.newBuilder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun fetch(
        url: String,
        userAgent: String = DEFAULT_UA,
        maxBytes: Long = 2_000_000,
    ): Result<String> {
        if (!isPublicHttpUrl(url)) {
            return Result.failure(IllegalArgumentException("blocked_url: only public http(s) URLs are allowed"))
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code} for $url"))
                }
                val finalUrl = response.request.url.toString()
                if (!isPublicHttpUrl(finalUrl)) {
                    return Result.failure(
                        IllegalArgumentException("blocked_redirect: '$url' redirected to a private address"),
                    )
                }
                val source = response.body?.source() ?: return Result.failure(IOException("empty body"))
                source.request(maxBytes)
                Result.success(source.buffer.clone().readUtf8())
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    fun fetchBytes(url: String, maxBytes: Long = 20_000_000L): Result<ByteArray> {
        if (!isPublicHttpUrl(url)) {
            return Result.failure(IllegalArgumentException("blocked_url: only public http(s) URLs are allowed"))
        }
        val request = Request.Builder().url(url).header("User-Agent", DEFAULT_UA).get().build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(IOException("HTTP ${response.code} for $url"))
                }
                val finalUrl = response.request.url.toString()
                if (!isPublicHttpUrl(finalUrl)) {
                    return Result.failure(
                        IllegalArgumentException("blocked_redirect: '$url' redirected to a private address"),
                    )
                }
                val source = response.body?.source() ?: return Result.failure(IOException("empty body"))
                source.request(maxBytes)
                val bytes = source.buffer.clone().readByteArray()
                if (bytes.size > maxBytes) {
                    Result.failure(IOException("file exceeds the ${maxBytes / 1_000_000} MB limit"))
                } else {
                    Result.success(bytes)
                }
            }
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
    }
}

/** Rejects loopback/private hosts to keep the model from probing the LAN. */
internal fun isPublicHttpUrl(raw: String): Boolean {
    val url = raw.toHttpUrlOrNull() ?: return false
    if (url.scheme != "http" && url.scheme != "https") return false
    val host = url.host.lowercase()
    if (host == "localhost" || host.endsWith(".local") || host.endsWith(".internal")) return false
    if (host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")) return false
    val parts = host.split('.')
    if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
        val a = parts[0].toInt()
        val b = parts[1].toInt()
        if (a == 10 || a == 127 || a == 0) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 169 && b == 254) return false
    }
    return true
}

class WebSearchTool @Inject constructor(
    private val fetcher: WebFetcher,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "web_search",
        description = "Search the web (DuckDuckGo with Bing fallback) and return titles, URLs and snippets.",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"},"limit":{"type":"integer","description":"1-10, default 6"}},"required":["query"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val query = (arguments["query"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'query' argument")
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 6).coerceIn(1, 10)

        val errors = mutableListOf<String>()
        val hits = searchDuckDuckGoLite(query, limit, errors)
            .ifEmpty { searchDuckDuckGo(query, limit, errors) }
            .ifEmpty { searchBing(query, limit, errors) }
            .ifEmpty { searchBaidu(query, limit, errors) }
        if (hits.isEmpty()) {
            return ToolResult.Failure(
                "No results for '$query'" +
                    (if (errors.isEmpty()) "." else " (${errors.joinToString("; ")})"),
            )
        }
        return ToolResult.Success(
            buildString {
                append("Results for '$query':\n")
                hits.forEachIndexed { index, hit ->
                    append("\n${index + 1}. ${hit.title}\n   ${hit.url}\n   ${hit.snippet.take(200)}")
                }
            },
        )
    }

    private fun searchDuckDuckGoLite(query: String, limit: Int, errors: MutableList<String>): List<SearchHit> {
        val url = "https://lite.duckduckgo.com/lite/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetcher.fetch(url).getOrElse { failure ->
            errors += "ddg-lite: ${failure.message}"
            return emptyList()
        }
        val doc = Jsoup.parse(html)
        return doc.select("a.result-link").mapNotNull { anchor ->
            val href = anchor.attr("href")
            if (!href.startsWith("http")) return@mapNotNull null
            val snippet = anchor.parent()?.parent()?.nextElementSibling()?.text().orEmpty()
            SearchHit(anchor.text(), href, snippet)
        }.take(limit)
    }

    private fun searchDuckDuckGo(query: String, limit: Int, errors: MutableList<String>): List<SearchHit> {
        val url = "https://html.duckduckgo.com/html/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetcher.fetch(url).getOrElse { failure ->
            errors += "duckduckgo: ${failure.message}"
            return emptyList()
        }
        val doc = Jsoup.parse(html)
        return doc.select("a.result__a").mapNotNull { anchor ->
            val href = anchor.attr("href")
            val resolved = if (href.contains("uddg=")) {
                runCatching {
                    URLDecoder.decode(
                        href.substringAfter("uddg=").substringBefore('&'),
                        "UTF-8",
                    )
                }.getOrNull()
            } else {
                href
            } ?: return@mapNotNull null
            val snippet = anchor.closest(".result")?.selectFirst(".result__snippet")?.text().orEmpty()
            SearchHit(anchor.text(), resolved, snippet)
        }.filter { it.url.startsWith("http") }.take(limit)
    }

    private fun searchBing(query: String, limit: Int, errors: MutableList<String>): List<SearchHit> {
        val url = "https://www.bing.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetcher.fetch(url).getOrElse { failure ->
            errors += "bing: ${failure.message}"
            return emptyList()
        }
        val doc = Jsoup.parse(html)
        return doc.select("li.b_algo").mapNotNull { item ->
            val anchor = item.selectFirst("h2 a") ?: return@mapNotNull null
            SearchHit(
                title = anchor.text(),
                url = anchor.attr("href"),
                snippet = item.selectFirst(".b_caption p")?.text().orEmpty(),
            )
        }.filter { it.url.startsWith("http") }.take(limit)
    }

    private fun searchBaidu(query: String, limit: Int, errors: MutableList<String>): List<SearchHit> {
        val url = "https://www.baidu.com/s?wd=" + java.net.URLEncoder.encode(query, "UTF-8")
        val html = fetcher.fetch(url).getOrElse { failure ->
            errors += "baidu: ${failure.message}"
            return emptyList()
        }
        val doc = Jsoup.parse(html)
        return doc.select("h3.t a, h3 a").mapNotNull { anchor ->
            val title = anchor.text()
            val href = anchor.attr("href")
            if (title.isBlank() || !href.startsWith("http")) return@mapNotNull null
            SearchHit(title, href, "")
        }.distinctBy { it.url }.take(limit)
    }
}

class WebReadTool @Inject constructor(
    private val fetcher: WebFetcher,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "web_read",
        description = "Fetch a web page and return its readable text (scripts/navigation stripped, truncated).",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"},"max_chars":{"type":"integer","description":"default 6000"}},"required":["url"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val url = (arguments["url"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'url' argument")
        val maxChars = ((arguments["max_chars"] as? JsonPrimitive)?.intOrNull ?: 6_000).coerceIn(500, 12_000)
        val html = fetcher.fetch(url).getOrElse { failure ->
            return ToolResult.Failure("Fetch failed: ${failure.message}")
        }
        val doc = Jsoup.parse(html, url)
        doc.select("script, style, nav, header, footer, aside, noscript, iframe, svg").remove()
        val title = doc.title()
        val text = (doc.body()?.text() ?: doc.text()).replace(Regex("\\s+"), " ").trim()
        if (text.isBlank()) return ToolResult.Failure("Page has no readable text.")
        val clipped = if (text.length > maxChars) text.take(maxChars) + "…(truncated)" else text
        return ToolResult.Success("$title\n\n$clipped")
    }
}

class GetWeatherTool @Inject constructor(
    private val fetcher: WebFetcher,
    private val json: Json,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "get_weather",
        description = "Current weather and a 3-day forecast for a city (no API key).",
        parametersJson = """{"type":"object","properties":{"city":{"type":"string"}},"required":["city"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val city = (arguments["city"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'city' argument")
        val url = "https://wttr.in/" + java.net.URLEncoder.encode(city, "UTF-8") + "?format=j1"
        val body = fetcher.fetch(url).getOrElse { failure ->
            return ToolResult.Failure("Weather lookup failed: ${failure.message}")
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return ToolResult.Failure("Weather response was not parseable.")
        val current = root["current_condition"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return ToolResult.Failure("No weather data for '$city'.")
        val desc = current["weatherDesc"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("value")?.jsonPrimitive?.content.orEmpty()
        val text = buildString {
            append("$city: $desc, ${current.str("temp_C")}°C (feels ${current.str("FeelsLikeC")}°C), ")
            append("humidity ${current.str("humidity")}%, wind ${current.str("windspeedKmph")} km/h\n")
            root["weather"]?.jsonArray?.take(3)?.forEach { day ->
                val d = day.jsonObject
                append("\n${d.str("date")}: ${d.str("mintempC")}~${d.str("maxtempC")}°C")
            }
        }
        return ToolResult.Success(text)
    }

    private fun JsonObject.str(key: String): String =
        (this[key] as? JsonPrimitive)?.content.orEmpty()
}

class FetchRssTool @Inject constructor(
    private val fetcher: WebFetcher,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "fetch_rss",
        description = "Parse an RSS/Atom feed and return recent items (title, link, date).",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"},"limit":{"type":"integer"}},"required":["url"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val url = (arguments["url"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'url' argument")
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 10).coerceIn(1, 30)
        val body = fetcher.fetch(url).getOrElse { failure ->
            return ToolResult.Failure("Feed fetch failed: ${failure.message}")
        }
        val doc = runCatching { Jsoup.parse(body, url, Parser.xmlParser()) }.getOrNull()
            ?: return ToolResult.Failure("Feed parse failed.")
        val items = doc.select("item, entry").take(limit)
        if (items.isEmpty()) return ToolResult.Failure("No RSS/Atom items found at this URL.")
        return ToolResult.Success(
            buildString {
                append("Feed items:\n")
                items.forEach { item ->
                    val title = item.selectFirst("title")?.text().orEmpty()
                    val link = item.selectFirst("link")?.text()
                        ?: item.selectFirst("link")?.attr("href").orEmpty()
                    val date = item.selectFirst("pubDate, updated, published")?.text().orEmpty()
                    append("\n- $title\n  $link${if (date.isBlank()) "" else "\n  $date"}")
                }
            },
        )
    }
}
