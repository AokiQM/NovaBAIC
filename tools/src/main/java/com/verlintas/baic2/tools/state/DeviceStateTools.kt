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
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import java.text.SimpleDateFormat
import java.util.Date
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

class GetLocationTool : DeviceTool {
    override val spec = ToolSpec(
        name = "get_location",
        description = "Last known GPS/network location with accuracy (needs location permission).",
        parametersJson = """{"type":"object","properties":{}}""",
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
            ?: return ToolResult.Failure("No recent location fix available. Open a maps app to get one.")
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(location.time))
        return ToolResult.Success(
            "%.5f, %.5f (±%.0fm, %s, %s)".format(
                Locale.ROOT,
                location.latitude,
                location.longitude,
                location.accuracy,
                location.provider,
                time,
            ),
        )
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
