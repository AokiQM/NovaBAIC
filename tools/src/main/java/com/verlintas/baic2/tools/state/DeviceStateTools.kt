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

package com.verlintas.baic2.tools.state

import android.Manifest
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.GeoCoordinates
import com.verlintas.baic2.core.model.GeoPoint
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import com.verlintas.baic2.tools.geo.Gazetteer
import java.util.Locale
import kotlinx.serialization.json.JsonObject

class GetScreenStateTool : DeviceTool {
    override val spec = ToolSpec(
        name = "get_screen_state",
        description = "Whether the screen is on and whether the device is locked.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val power = context.appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = context.appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val interactive = power.isInteractive
        val locked = keyguard.isKeyguardLocked
        return ToolResult.Success(
            "screen ${if (interactive) "on" else "off"}; ${if (locked) "locked" else "unlocked"}",
        )
    }
}

class GetForegroundAppTool : DeviceTool {
    override val spec = ToolSpec(
        name = "get_foreground_app",
        description = "Package name of the app currently on screen (needs the accessibility service).",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val pkg = context.accessibility.foregroundPackage()
            ?: return ToolResult.Failure(
                "Foreground app unknown. Enable the BAIC2 accessibility service in 设置 → 无障碍.",
            )
        return ToolResult.Success(pkg)
    }
}

class GetLocationTool(
    private val gazetteer: Gazetteer,
) : DeviceTool {
    override val spec = ToolSpec(
        name = "get_location",
        description = "Explain where the phone is, fully offline: raw WGS-84 coordinates, GCJ-02/BD-09 " +
            "values for map apps, fix age/accuracy, and the nearest gazetteer places with distance " +
            "and compass direction. No Geocoder, no GMS, nothing persisted. Call ONLY when the user " +
            "mentions a place, arriving somewhere, being lost, asking where they are or otherwise " +
            "genuinely needs location - do not call it on ordinary turns. Location facts are " +
            "perishable: when writing one to memory, pass when and a short expires (e.g. 12h).",
        parametersJson = """{"type":"object","properties":{"limit":{"type":"integer","description":"nearest places to list, 1-6, default 4"}}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val fine = context.isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!fine && !coarse) {
            return ToolResult.Failure(
                "Location permission missing. Ask the user to grant location access to this app.",
            )
        }
        val manager = context.appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val location = providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            .maxByOrNull { it.time }
            ?: return ToolResult.Failure(
                "No recent location fix available. Open a map app to get one, then try again.",
            )
        val limit = ((arguments["limit"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toIntOrNull() ?: 4).coerceIn(1, 6)
        val origin = GeoPoint(location.latitude, location.longitude)
        val gcj = GeoCoordinates.wgs84ToGcj02(origin)
        val bd = GeoCoordinates.gcj02ToBd09(gcj)
        val nearest = gazetteer.nearest(location.latitude, location.longitude, limit = limit)
        val anchor = gazetteer.nearest(
            location.latitude,
            location.longitude,
            limit = 1,
            kinds = com.verlintas.baic2.core.model.GazetteerPlace.KIND_PROVINCE..
                com.verlintas.baic2.core.model.GazetteerPlace.KIND_COUNTY,
        ).firstOrNull()
        val ageMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0)
        return ToolResult.Success(
            buildString {
                append(
                    "Location: %.5f, %.5f (WGS-84) · ±%.0fm · %s · %s\n".format(
                        Locale.ROOT,
                        location.latitude,
                        location.longitude,
                        location.accuracy,
                        location.provider,
                        formatAge(ageMs),
                    ),
                )
                append(
                    "Map coordinates: GCJ-02 %.5f, %.5f · BD-09 %.5f, %.5f\n".format(
                        Locale.ROOT, gcj.latitude, gcj.longitude, bd.latitude, bd.longitude,
                    ),
                )
                if (nearest.isEmpty()) {
                    append("No offline gazetteer place nearby (outside China coverage?).\n")
                } else {
                    append("Nearest places (offline gazetteer, ${Gazetteer.ATTRIBUTION}):\n")
                    nearest.forEach { place ->
                        append("- ${place.name} (${place.kindLabel}) · ")
                            .append(formatDistance(place.distanceMeters)).append(' ')
                            .append(bearingLabel(place.bearingDegrees)).append('\n')
                    }
                }
                anchor?.let { place ->
                    append("Anchor: ${place.name} (${place.kindLabel}) is ")
                        .append(formatDistance(place.distanceMeters)).append(' ')
                        .append(bearingLabel(place.bearingDegrees)).append(" of here.\n")
                }
                if (ageMs > STALE_FIX_MS) {
                    append("Note: this fix is ").append(formatAge(ageMs))
                        .append(" old - ask the user or have them open a map app for a fresh one.\n")
                }
                append("Show it on a map with open_map (GCJ-02 handled there).")
            },
        )
    }

    private fun formatDistance(meters: Double): String =
        if (meters < 1_000) "${meters.toInt()} m"
        else "%.1f km".format(Locale.ROOT, meters / 1_000)

    private fun formatAge(ageMs: Long): String = when {
        ageMs < 60_000 -> "just now"
        ageMs < 3_600_000 -> "${ageMs / 60_000} min ago"
        else -> "${ageMs / 3_600_000} h ago"
    }

    private fun bearingLabel(degrees: Double): String = when {
        degrees < 22.5 || degrees >= 337.5 -> "north"
        degrees < 67.5 -> "northeast"
        degrees < 112.5 -> "east"
        degrees < 157.5 -> "southeast"
        degrees < 202.5 -> "south"
        degrees < 247.5 -> "southwest"
        degrees < 292.5 -> "west"
        else -> "northwest"
    }

    private companion object {
        const val STALE_FIX_MS = 5 * 60_000L
    }
}

class GetAppUsageTool : DeviceTool {
    override val spec = ToolSpec(
        name = "get_app_usage",
        description = "Most used apps in the last 24 hours with foreground time (needs Usage access).",
        parametersJson = """{"type":"object","properties":{"limit":{"type":"integer","description":"default 10"}}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val appOps = context.appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.appContext.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.appContext.packageName,
            )
        }
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return ToolResult.Failure(
                "Usage access not granted. Ask the user to enable it in 设置 → 应用 → 特殊权限 → 使用情况访问.",
            )
        }
        val limit = ((arguments["limit"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 10)
            .coerceIn(1, 30)
        val manager = context.appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000, now)
            .filter { it.totalTimeInForeground > 0 }
            .sortedByDescending { it.totalTimeInForeground }
            .take(limit)
        if (stats.isEmpty()) return ToolResult.Success("No usage data for the last 24 hours.")
        val pm = context.appContext.packageManager
        return ToolResult.Success(
            buildString {
                append("Top apps (last 24h):\n")
                stats.forEach { entry ->
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(entry.packageName, 0)).toString()
                    }.getOrDefault(entry.packageName)
                    val minutes = entry.totalTimeInForeground / 60_000
                    append("\n- $label (${entry.packageName}): ${minutes}m")
                }
            },
        )
    }
}
