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

package com.verlintas.baic2.tools.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.verlintas.baic2.tools.uiDistance

data class AppMatch(
    val packageName: String,
    val label: String,
    val score: Int,
)

/**
 * Resolves fuzzy app references ("微信", "settings", "com.tencent.mm") to
 * packages so the model never has to call list_installed_apps first.
 */
object AppResolver {

    fun resolve(
        context: Context,
        query: String,
        launchableOnly: Boolean = false,
        limit: Int = 8,
    ): List<AppMatch> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)
            .asSequence()
            .filter { app ->
                !launchableOnly || pm.getLaunchIntentForPackage(app.packageName) != null
            }
            .mapNotNull { app ->
                val label = pm.getApplicationLabel(app).toString()
                scoreMatch(label, app.packageName, needle)?.let { score ->
                    AppMatch(app.packageName, label, score)
                }
            }
            .sortedWith(compareBy({ it.score }, { it.label.lowercase() }))
            .distinctBy { it.packageName }
            .take(limit)
            .toList()
    }

    fun isSystemApp(context: Context, packageName: String): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)

    /** Pure scoring: lower is better, null means no match. */
    internal fun scoreMatch(label: String, packageName: String, query: String): Int? {
        val l = label.trim().lowercase()
        val p = packageName.trim().lowercase()
        val q = query.trim().lowercase()
        if (q.isEmpty()) return null
        return when {
            p == q || l == q -> 0
            l.startsWith(q) -> 1
            l.contains(q) -> 2
            p.contains(q) -> 3
            q.contains(l) && l.length >= 2 -> 4
            q.length >= 4 && uiDistance(l, q) <= 2 -> 5
            else -> null
        }
    }
}
