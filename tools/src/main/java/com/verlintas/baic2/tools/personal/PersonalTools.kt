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

package com.verlintas.baic2.tools.personal

import android.Manifest
import android.content.Intent
import android.provider.ContactsContract
import android.provider.CalendarContract
import android.net.Uri
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.device.api.NotificationCache
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class ReadNotificationsTool : DeviceTool {
    override val spec = ToolSpec(
        name = "read_notifications",
        untrustedOutput = true,
        description = "Read recent notifications (needs notification-listener access).",
        parametersJson = """{"type":"object","properties":{"limit":{"type":"integer"},"hours":{"type":"integer","description":"look-back window, default 12"},"app":{"type":"string","description":"package substring filter"},"query":{"type":"string","description":"keyword filter on title/text"}}}""",
        readOnly = true,
        danger = DangerLevel.MEDIUM,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val enabled = android.provider.Settings.Secure.getString(
            context.appContext.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        // Colon-separated flattened components; substring matching would treat
        // "com.evil.pkg.verlintas.baic2" as a match.
        val packageName = context.appContext.packageName
        val granted = enabled.split(':').any { entry ->
            val component = entry.trim()
            component == packageName || component.startsWith("$packageName/")
        }
        if (!granted) {
            return ToolResult.Failure(
                "Notification access is not granted. Ask the user to enable it in " +
                    "设置 → 通知 → 通知使用权 → BAIC2.",
            )
        }
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 20).coerceIn(1, 50)
        val hours = ((arguments["hours"] as? JsonPrimitive)?.intOrNull ?: 12).coerceIn(1, 72)
        val app = (arguments["app"] as? JsonPrimitive)?.contentOrNull
        val query = (arguments["query"] as? JsonPrimitive)?.contentOrNull
            ?.trim()?.takeIf { it.isNotBlank() }
        val since = System.currentTimeMillis() - hours * 3_600_000L
        val items = NotificationCache.snapshot(limit, since, app)
            .filter { item ->
                query == null ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.text.contains(query, ignoreCase = true)
            }
        if (items.isEmpty()) {
            return ToolResult.Success(
                if (query == null) "No recent notifications." else "No notifications match '$query'.",
            )
        }
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return ToolResult.Success(
            buildString {
                append("Recent notifications:\n")
                items.forEach { item ->
                    append("\n- [${format.format(Date(item.postedAt))}] ${item.packageName}")
                    if (item.title.isNotBlank()) append(" · ${item.title}")
                    if (item.text.isNotBlank()) append(": ${item.text.take(160)}")
                }
            },
        )
    }
}

class SearchContactsTool : DeviceTool {
    override val spec = ToolSpec(
        name = "search_contacts",
        description = "Search contacts by name and return matching phone numbers.",
        parametersJson = """{"type":"object","properties":{"name":{"type":"string"},"limit":{"type":"integer"}},"required":["name"]}""",
        readOnly = true,
        danger = DangerLevel.MEDIUM,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val name = (arguments["name"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'name' argument")
        if (!context.isGranted(Manifest.permission.READ_CONTACTS)) {
            return ToolResult.Failure(
                "Contacts permission missing. Ask the user to grant contacts access to this app.",
            )
        }
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 10).coerceIn(1, 30)
        val results = mutableListOf<String>()
        runCatching {
            context.appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                while (cursor.moveToNext() && results.size < limit) {
                    val display = cursor.getString(0) ?: continue
                    val number = cursor.getString(1).orEmpty()
                    results += "$display — $number"
                }
            }
        }.onFailure { failure ->
            return ToolResult.Failure("Contacts query failed: ${failure.message}")
        }
        if (results.isEmpty()) return ToolResult.Failure("No contacts match '$name'.")
        return ToolResult.Success("Contacts:\n" + results.joinToString("\n") { "- $it" })
    }
}

class SendEmailTool : DeviceTool {
    override val spec = ToolSpec(
        name = "send_email",
        description = "Compose an email in the user's mail app (the user confirms the send).",
        parametersJson = """{"type":"object","properties":{"to":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"}},"required":["to"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val to = (arguments["to"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'to' argument")
        val subject = (arguments["subject"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val body = (arguments["body"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(to)}")).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(intent)
            ToolResult.Success("Mail composer opened for $to")
        } catch (e: Exception) {
            ToolResult.Failure("No mail app available: ${e.message}")
        }
    }
}

class CreateCalendarEventTool : DeviceTool {
    override val spec = ToolSpec(
        name = "create_calendar_event",
        description = "Open the calendar pre-filled with an event the user can confirm.",
        parametersJson = """{"type":"object","properties":{"title":{"type":"string"},"start_epoch_ms":{"type":"integer"},"end_epoch_ms":{"type":"integer"},"description":{"type":"string"}},"required":["title","start_epoch_ms"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val title = (arguments["title"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'title' argument")
        val start = (arguments["start_epoch_ms"] as? JsonPrimitive)?.longOrNull
            ?: return ToolResult.Failure("Missing 'start_epoch_ms'")
        val end = (arguments["end_epoch_ms"] as? JsonPrimitive)?.longOrNull ?: (start + 3_600_000)
        val description = (arguments["description"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.appContext.startActivity(intent)
            ToolResult.Success("Calendar composer opened for '$title'")
        } catch (e: Exception) {
            ToolResult.Failure("No calendar app available: ${e.message}")
        }
    }
}
