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

package com.verlintas.baic2.feature.settings

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class ReleaseInfo(
    val tag: String,
    val url: String,
    val notes: String?,
)

/** Minimal GitHub releases check (no token, 8s timeouts, failures ignored). */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Verlintas/NovaBAIC/releases/latest"

    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "BetterAIChat2")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    return@runCatching null
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val root = Json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
                val tag = (root["tag_name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                ReleaseInfo(
                    tag = tag,
                    url = (root["html_url"] as? JsonPrimitive)?.content.orEmpty(),
                    notes = (root["body"] as? JsonPrimitive)?.content,
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }
}
