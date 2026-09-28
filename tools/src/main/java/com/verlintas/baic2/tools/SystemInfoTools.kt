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

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlinx.serialization.json.JsonObject

class DeviceInfoTool : DeviceTool {
    override val spec = ToolSpec(
        name = "device_info",
        description = "Device model, Android version, battery level/charging state and storage usage.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val batteryManager = context.appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = batteryManager.isCharging

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1_073_741_824.0
        val totalGb = stat.totalBytes / 1_073_741_824.0

        val text = buildString {
            append("${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Battery: $batteryLevel%${if (charging) " (charging)" else ""}\n")
            append(String.format(java.util.Locale.ROOT, "Storage: %.1f GB free / %.1f GB", freeGb, totalGb))
        }
        return ToolResult.Success(text)
    }
}

class NetworkStatusTool : DeviceTool {
    override val spec = ToolSpec(
        name = "network_status",
        description = "Current network type (wifi/mobile/ethernet), whether the internet is reachable and metered state.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val cm = context.appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
            ?: return ToolResult.Success("No active network")
        val capabilities = cm.getNetworkCapabilities(network)
            ?: return ToolResult.Success("Network present but capabilities unknown")

        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return ToolResult.Success(
            "$transport; internet=${if (validated) "yes" else "unverified"}; metered=${if (metered) "yes" else "no"}",
        )
    }
}
