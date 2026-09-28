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
import com.verlintas.baic2.core.engine.ToolRunContext
import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.device.api.AccessibilityBridge
import com.verlintas.baic2.device.api.OcrProvider
import com.verlintas.baic2.device.api.ScreenshotProvider
import kotlinx.serialization.json.JsonObject

/** Android permission probe, injectable so tools stay testable. */
fun interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

data class ToolContext(
    val appContext: Context,
    val permissions: PermissionChecker,
    val screenshot: ScreenshotProvider,
    val ocr: OcrProvider,
    val accessibility: AccessibilityBridge,
    val run: ToolRunContext = ToolRunContext(null, AppMode.CHAT),
) {
    fun isGranted(permission: String): Boolean = permissions.isGranted(permission)
}

/**
 * A device capability exposed to the model. Parameters are validated by the
 * tool itself; failures must be actionable.
 */
interface DeviceTool {
    val spec: ToolSpec

    suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult
}
