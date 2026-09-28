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

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

class ClipboardGetTool : DeviceTool {
    override val spec = ToolSpec(
        name = "get_clipboard",
        description = "Read the current clipboard text.",
        parametersJson = """{"type":"object","properties":{}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val clipboard = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context.appContext)?.toString()
        return if (text.isNullOrBlank()) ToolResult.Success("Clipboard is empty.") else ToolResult.Success(text)
    }
}

class ClipboardSetTool : DeviceTool {
    override val spec = ToolSpec(
        name = "set_clipboard",
        description = "Copy text to the clipboard.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'text' argument")
        val clipboard = context.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("baic2", text))
        return ToolResult.Success("Copied ${text.length} characters to the clipboard.")
    }
}

class ShareTextTool : DeviceTool {
    override val spec = ToolSpec(
        name = "share_text",
        description = "Open the system share sheet with text or a link.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"},"title":{"type":"string"}},"required":["text"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'text' argument")
        val title = (arguments["title"] as? JsonPrimitive)?.content
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            title?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ToolResult.Success("Share sheet opened.")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open share sheet: ${e.message}")
        }
    }
}

class OpenDialerTool : DeviceTool {
    override val spec = ToolSpec(
        name = "open_dialer",
        description = "Open the dialer pre-filled with a number (does not place the call).",
        parametersJson = """{"type":"object","properties":{"number":{"type":"string"}},"required":["number"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val number = (arguments["number"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'number' argument")
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.appContext.startActivity(intent)
            ToolResult.Success("Dialer opened with $number")
        } catch (e: Exception) {
            ToolResult.Failure("Could not open dialer: ${e.message}")
        }
    }
}

class VibrateTool : DeviceTool {
    override val spec = ToolSpec(
        name = "vibrate",
        description = "Vibrate the device briefly as haptic feedback.",
        parametersJson = """{"type":"object","properties":{"duration_ms":{"type":"integer","description":"50-2000, default 200"}}}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    @android.annotation.SuppressLint("MissingPermission")
    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        if (!context.isGranted(Manifest.permission.VIBRATE)) {
            return ToolResult.Failure("VIBRATE permission missing. Reinstall the app to restore it.")
        }
        val duration = ((arguments["duration_ms"] as? JsonPrimitive)?.intOrNull ?: 200).coerceIn(50, 2000)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.appContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(duration.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
        return ToolResult.Success("Vibrated ${duration}ms")
    }
}

class MediaControlTool : DeviceTool {
    override val spec = ToolSpec(
        name = "media_control",
        description = "Control the active media session: play, pause, toggle, next, previous, stop.",
        parametersJson = """{"type":"object","properties":{"action":{"type":"string","description":"play|pause|toggle|next|previous|stop"}},"required":["action"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = (arguments["action"] as? JsonPrimitive)?.content?.lowercase()
            ?: return ToolResult.Failure("Missing 'action' argument")
        val keyCode = when (action) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return ToolResult.Failure("Unknown action '$action'")
        }
        val audio = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return ToolResult.Success("Sent media key: $action")
    }
}

class SetVolumeTool : DeviceTool {
    override val spec = ToolSpec(
        name = "set_volume",
        description = "Set the media volume to a percentage (0-100) or nudge it up/down.",
        parametersJson = """{"type":"object","properties":{"level":{"type":"integer","description":"0-100"},"delta":{"type":"string","description":"up|down"}}}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val audio = context.appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val level = (arguments["level"] as? JsonPrimitive)?.intOrNull
        val delta = (arguments["delta"] as? JsonPrimitive)?.content?.lowercase()

        val target = when {
            level != null -> (max * level.coerceIn(0, 100) / 100.0).toInt()
            delta == "up" -> (current + 1).coerceAtMost(max)
            delta == "down" -> (current - 1).coerceAtLeast(0)
            else -> return ToolResult.Failure("Provide 'level' (0-100) or 'delta' (up/down)")
        }
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        val percent = if (max == 0) 0 else target * 100 / max
        return ToolResult.Success("Media volume set to $percent%")
    }
}

class SetBrightnessTool : DeviceTool {
    override val spec = ToolSpec(
        name = "set_brightness",
        description = "Set screen brightness percentage (0-100) or enable automatic brightness.",
        parametersJson = """{"type":"object","properties":{"level":{"type":"integer"},"auto":{"type":"boolean"}}}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val resolver = context.appContext.contentResolver
        if (!Settings.System.canWrite(context.appContext)) {
            return ToolResult.Failure(
                "Missing Modify-system-settings permission. Ask the user to open 设置 → 应用 → 特殊权限 → " +
                    "修改系统设置 and enable it for this app.",
            )
        }
        val auto = (arguments["auto"] as? JsonPrimitive)?.booleanOrNull
        if (auto == true) {
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            )
            return ToolResult.Success("Automatic brightness enabled.")
        }
        val level = (arguments["level"] as? JsonPrimitive)?.intOrNull
            ?: return ToolResult.Failure("Provide 'level' (0-100) or auto=true")
        val value = (255 * level.coerceIn(1, 100) / 100.0).toInt()
        Settings.System.putInt(
            resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return ToolResult.Success("Screen brightness set to $level%")
    }
}

class SetFlashlightTool : DeviceTool {
    override val spec = ToolSpec(
        name = "set_flashlight",
        description = "Turn the flashlight (torch) on or off.",
        parametersJson = """{"type":"object","properties":{"on":{"type":"boolean"}},"required":["on"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        if (!context.isGranted(Manifest.permission.CAMERA)) {
            return ToolResult.Failure(
                "Camera permission is required for the flashlight. " +
                    "Grant it in system settings, then retry.",
            )
        }
        val on = (arguments["on"] as? JsonPrimitive)?.booleanOrNull
            ?: return ToolResult.Failure("Missing 'on' argument")
        val manager = context.appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ToolResult.Failure("This device has no flashlight.")
        return try {
            manager.setTorchMode(cameraId, on)
            ToolResult.Success("Flashlight ${if (on) "on" else "off"}")
        } catch (e: Exception) {
            ToolResult.Failure("Could not toggle the flashlight: ${e.message}")
        }
    }
}

class SendNotificationTool : DeviceTool {
    private val nextId = AtomicInteger(1)

    override val spec = ToolSpec(
        name = "send_notification",
        description = "Post a system notification with a title and text.",
        parametersJson = """{"type":"object","properties":{"title":{"type":"string"},"text":{"type":"string"}},"required":["title","text"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val title = (arguments["title"] as? JsonPrimitive)?.content ?: "BAIC2"
        val text = (arguments["text"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'text' argument")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !context.isGranted(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            return ToolResult.Failure(
                "Notification permission missing. Grant notifications for this app, then retry.",
            )
        }
        val manager = context.appContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        ensureChannel(manager)
        val notification = android.app.Notification.Builder(context.appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .build()
        manager.notify(nextId.getAndIncrement(), notification)
        return ToolResult.Success("Notification posted: $title")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensureChannel(manager: android.app.NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL_ID,
                    "BAIC2",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "baic2_tools"
    }
}
