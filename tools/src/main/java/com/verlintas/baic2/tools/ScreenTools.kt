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
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import java.io.File
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun screenshotFailure(cause: Throwable?): ToolResult.Failure {
    val message = cause?.message.orEmpty()
    return if (message.contains("not_authorized")) {
        ToolResult.Failure(
            "Screen capture permission is missing. Ask the user to tap ⋮ → 分析屏幕 in the chat screen " +
                "and approve the system dialog.",
        )
    } else {
        ToolResult.Failure("Screen capture failed (${message.ifBlank { "unknown" }}). Try again.")
    }
}

class TakeScreenshotTool : DeviceTool {
    override val spec = ToolSpec(
        name = "take_screenshot",
        description = "Capture the current screen and save it as a PNG file; returns the file path.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
        return try {
            val directory = File(context.appContext.filesDir, "screenshots").apply { mkdirs() }
            val file = File(directory, "screenshot_${System.currentTimeMillis()}.png")
            file.writeBytes(bytes)
            ToolResult.Success(
                "Screenshot saved: ${file.absolutePath} (${bytes.size / 1024} KB). " +
                    "Use screen_ocr to read its text, or ui_find to locate elements.",
            )
        } catch (e: Exception) {
            ToolResult.Failure("Could not save the screenshot: ${e.message}")
        }
    }
}

class ScreenOcrTool : DeviceTool {
    override val spec = ToolSpec(
        name = "screen_ocr",
        description = "Capture the screen and return its visible text (Chinese + English, per line). " +
            "Use it to read what is on screen.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
        val lines = context.ocr.recognize(bytes).getOrElse { error ->
            return ToolResult.Failure("OCR failed: ${error.message ?: "unknown error"}")
        }
        if (lines.isEmpty()) return ToolResult.Success("No text detected on screen.")
        val text = lines.joinToString("\n") { it.text }
        val clipped = if (text.length > SCREEN_TEXT_LIMIT) {
            text.take(SCREEN_TEXT_LIMIT) + "\n…(truncated)"
        } else {
            text
        }
        return ToolResult.Success("Screen text (${lines.size} lines):\n$clipped")
    }

    private companion object {
        const val SCREEN_TEXT_LIMIT = 6_000
    }
}

