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

import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.device.api.RecordingResult
import com.verlintas.baic2.device.api.ScreenRecorderBridge
import com.verlintas.baic2.device.api.SpeechInputBridge
import com.verlintas.baic2.device.api.TranscriptionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.Locale

/** Short video-only screen recording for flows that need motion. */
class ScreenRecordTool(
    private val recorder: ScreenRecorderBridge,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "screen_record",
        description = "Record the screen as a short MP4 (video only, max 120s) and return the file " +
            "path. Uses the same screen-capture authorization as screenshots; screenshots are " +
            "paused while recording.",
        parametersJson = """{"type":"object","properties":{"seconds":{"type":"integer","description":"1-120, default 10"}}}""",
        readOnly = true,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val seconds = ((arguments["seconds"] as? JsonPrimitive)?.intOrNull ?: 10).coerceIn(1, 120)
        return when (val result = recorder.record(seconds * 1_000L)) {
            is RecordingResult.Recorded -> {
                val megabytes = result.sizeBytes / 1_048_576.0
                ToolResult.Success(
                    String.format(
                        Locale.ROOT,
                        "Recorded %ds (%.1f MB): %s",
                        result.durationMs / 1000,
                        megabytes,
                        result.filePath,
                    ),
                )
            }

            is RecordingResult.Unavailable -> ToolResult.Failure("${result.reason}")
            is RecordingResult.Failed -> ToolResult.Failure("recording failed — ${result.reason}")
        }
    }
}

/** One bounded spoken utterance, transcribed on-device. */
class TranscribeAudioTool(
    private val speech: SpeechInputBridge,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "transcribe_audio",
        untrustedOutput = true,
        description = "Listen for a spoken utterance (up to 30s) and return its transcription. " +
            "Use for voice notes and quick dictation; ask the user to speak first.",
        parametersJson = """{"type":"object","properties":{"seconds":{"type":"integer","description":"3-30, default 8"}}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val seconds = ((arguments["seconds"] as? JsonPrimitive)?.intOrNull ?: 8).coerceIn(3, 30)
        return when (val result = speech.transcribe(seconds * 1_000L)) {
            is TranscriptionResult.Text -> ToolResult.Success(result.value)
            TranscriptionResult.Timeout ->
                ToolResult.Failure("no speech detected within ${seconds}s")
            is TranscriptionResult.Unavailable -> ToolResult.Failure("${result.reason}")
        }
    }
}
