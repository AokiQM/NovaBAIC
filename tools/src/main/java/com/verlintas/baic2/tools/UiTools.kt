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
import com.verlintas.baic2.device.api.TextNode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Fuzzy match score for a visible label: 0 exact, 1 normalized-exact,
 * 2 prefix, 3 substring, 4 label-inside-query, 5 edit distance <= 2.
 * Null means no reasonable match.
 */
internal fun uiMatchScore(label: String, query: String): Int? {
    val normalizedLabel = label.trim().lowercase()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedLabel.isEmpty() || normalizedQuery.isEmpty()) return null
    if (normalizedLabel == normalizedQuery) return 0
    val stripped = { value: String -> value.replace(Regex("[\\p{Punct}\\s]+"), "") }
    if (stripped(normalizedLabel) == stripped(normalizedQuery)) return 1
    if (normalizedLabel.startsWith(normalizedQuery)) return 2
    if (normalizedLabel.contains(normalizedQuery)) return 3
    if (normalizedQuery.contains(normalizedLabel)) return 4
    if (normalizedQuery.length >= 3 && uiDistance(normalizedLabel, normalizedQuery) <= 2) return 5
    return null
}

/** Best matches first, stable for equal scores. */
internal fun uiBestMatches(nodes: List<TextNode>, query: String, limit: Int = 10): List<TextNode> =
    nodes
        .mapNotNull { node -> uiMatchScore(node.text, query)?.let { node to it } }
        .sortedBy { it.second }
        .map { it.first }
        .distinctBy { "${it.text}@${it.centerX},${it.centerY}" }
        .take(limit)

internal fun uiDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}

/**
 * One smart UI tool instead of five dumb ones: locating (with scroll-to-find
 * and OCR fallback), tapping, typing, keys, scrolling, waiting and screen
 * text all live here. Tap/long-press resolve their target and verify that the
 * screen actually changed, so the model rarely needs a follow-up call.
 */
class UiControlTool : DeviceTool {

    override val spec = ToolSpec(
        name = "ui_control",
        description = "Control the UI through accessibility: tap/long-press by text (fuzzy match, " +
            "scrolls to find it, verifies the screen changed) or by x/y, type into the focused " +
            "field (optional submit), press_key (back|home|recents|notifications|enter), scroll, " +
            "wait_for a text, find (scored matches with coordinates), or dump screen_text.",
        parametersJson = """{"type":"object","properties":{"action":{"type":"string","enum":["tap","long_press","type","press_key","scroll","wait_for","find","screen_text"]},"text":{"type":"string","description":"target text for tap/long_press/wait_for/find"},"x":{"type":"integer"},"y":{"type":"integer"},"value":{"type":"string","description":"text to type, or the key for press_key"},"submit":{"type":"boolean","description":"press enter after typing"},"direction":{"type":"string","enum":["up","down","left","right"]},"distance":{"type":"integer","description":"scroll distance in px"},"timeout_ms":{"type":"integer","description":"wait_for timeout, default 6000"}},"required":["action"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = (arguments["action"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: return ToolResult.Failure("Missing 'action' argument")
        return when (action) {
            "tap" -> pointerAction(context, arguments, long = false)
            "long_press" -> pointerAction(context, arguments, long = true)
            "type" -> typeAction(context, arguments)
            "press_key" -> pressKeyAction(context, arguments)
            "scroll" -> scrollAction(context, arguments)
            "wait_for" -> waitAction(context, arguments)
            "find" -> findAction(context, arguments)
            "screen_text" -> screenTextAction(context)
            else -> ToolResult.Failure(
                "Unknown action '$action'. Use tap|long_press|type|press_key|scroll|wait_for|find|screen_text.",
            )
        }
    }

    // ------------------------------------------------------------------ tap

    private suspend fun pointerAction(
        context: ToolContext,
        arguments: JsonObject,
        long: Boolean,
    ): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
        val x = (arguments["x"] as? JsonPrimitive)?.intOrNull
        val y = (arguments["y"] as? JsonPrimitive)?.intOrNull

        val target: Pair<Int, Int>
        val matchNote: String
        if (text != null) {
            val resolved = resolveTextTarget(context, text)
                ?: return ToolResult.Failure(notFoundMessage(context, text))
            target = resolved.first.centerX to resolved.first.centerY
            matchNote = "match=\"${resolved.first.text}\"${if (resolved.second) ", scrolled into view" else ""}"
        } else if (x != null && y != null) {
            target = x to y
            matchNote = "coordinates"
        } else {
            return ToolResult.Failure("Provide 'text' or both 'x' and 'y'")
        }

        val before = screenSignature(context)
        val gesture = if (long) {
            context.accessibility.longPress(target.first, target.second)
        } else {
            context.accessibility.tap(target.first, target.second)
        }
        val failure = gesture.exceptionOrNull()
        if (failure != null) return accessibilityFailure(failure)

        delay(SETTLE_DELAY_MS)
        val after = screenSignature(context)
        val changed = before != after
        val summary = buildString {
            append(if (long) "Long-pressed " else "Tapped ")
            append("(${target.first}, ${target.second}) [$matchNote]. ")
            append(if (changed) "Screen changed:" else "Screen unchanged:")
            append(' ').append(after)
        }
        return ToolResult.Success(summary)
    }

    /**
     * Finds the best visible match, scrolling down (then restoring position)
     * when it is not on screen yet. Accessibility tree first, OCR as fallback.
     */
    private suspend fun resolveTextTarget(context: ToolContext, query: String): Pair<TextNode, Boolean>? {
        bestMatch(context, query)?.let { return it to false }

        val scrolls = mutableListOf<Int>()
        repeat(MAX_SCROLLS) { index ->
            val travelled = scrollBy(context, "down", null) ?: return@repeat
            scrolls += travelled
            delay(SCROLL_SETTLE_MS)
            bestMatch(context, query)?.let { return it to true }
        }
        // Not found: put the screen back where it was so the model keeps its map.
        scrolls.reversed().forEach { distance ->
            scrollBy(context, "up", distance)?.let { delay(SCROLL_SETTLE_MS) }
        }
        return null
    }

    private suspend fun bestMatch(context: ToolContext, query: String): TextNode? {
        uiBestMatches(context.accessibility.findText(query), query, limit = 1).firstOrNull()?.let { return it }
        val bytes = context.screenshot.capture().getOrNull() ?: return null
        val lines = context.ocr.recognize(bytes).getOrNull() ?: return null
        return uiBestMatches(
            lines.map { TextNode(it.text, it.left, it.top, it.right, it.bottom) },
            query,
            limit = 1,
        ).firstOrNull()
    }

    private fun notFoundMessage(context: ToolContext, query: String): String {
        val visible = context.accessibility.screenText(30).lines().filter { it.isNotBlank() }.take(12)
        return buildString {
            append("'").append(query).append("' was not found on screen")
            if (visible.isNotEmpty()) {
                append(". Visible now: ").append(visible.joinToString(" | "))
                append(". Use ui_control find with a shorter keyword or screen_text to inspect.")
            } else {
                append(". Accessibility service may be disabled; check Settings → Accessibility.")
            }
        }
    }

    // ----------------------------------------------------------------- type

    private suspend fun typeAction(context: ToolContext, arguments: JsonObject): ToolResult {
        val value = (arguments["value"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'value' (text to type)")
        if (!context.accessibility.editableFocused()) {
            return ToolResult.Failure(
                "No input field is focused. Tap the field first (action=tap, text=<field hint>), then type.",
            )
        }
        val typed = context.accessibility.typeText(value)
        typed.exceptionOrNull()?.let { return accessibilityFailure(it) }
        val submit = (arguments["submit"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!submit) return ToolResult.Success("Typed ${value.length} characters.")
        val enter = context.accessibility.pressKey("enter")
        return if (enter.isSuccess) {
            ToolResult.Success("Typed ${value.length} characters and pressed enter.")
        } else {
            ToolResult.Success(
                "Typed ${value.length} characters. Enter unavailable: ${enter.exceptionOrNull()?.message}",
            )
        }
    }

    // ------------------------------------------------------------- key/scroll

    private suspend fun pressKeyAction(context: ToolContext, arguments: JsonObject): ToolResult {
        val key = (arguments["value"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: (arguments["text"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: return ToolResult.Failure("Missing 'value' (back|home|recents|notifications|enter)")
        val before = screenSignature(context)
        val result = context.accessibility.pressKey(key)
        result.exceptionOrNull()?.let { return accessibilityFailure(it) }
        delay(SETTLE_DELAY_MS)
        val after = screenSignature(context)
        return ToolResult.Success("Pressed '$key'. Now on: $after (was: $before)")
    }

    private suspend fun scrollAction(context: ToolContext, arguments: JsonObject): ToolResult {
        val direction = (arguments["direction"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: return ToolResult.Failure("Missing 'direction' (up|down|left|right)")
        val distance = (arguments["distance"] as? JsonPrimitive)?.intOrNull
        scrollBy(context, direction, distance)
            ?: return ToolResult.Failure("Cannot scroll: accessibility service is not enabled.")
        delay(SCROLL_SETTLE_MS)
        val visible = context.accessibility.screenText(12).lines().filter { it.isNotBlank() }.take(5)
        return ToolResult.Success(
            "Scrolled $direction" +
                if (visible.isEmpty()) "." else ". Now visible: ${visible.joinToString(" | ")}",
        )
    }

    private suspend fun scrollBy(context: ToolContext, direction: String, distance: Int?): Int? {
        val (width, height) = context.accessibility.screenSize() ?: return null
        val cx = width / 2
        val cy = height / 2
        val travel = distance?.coerceIn(50, maxOf(height, width))
            ?: when (direction) {
                "up", "down" -> (height * 0.4f).toInt()
                else -> (width * 0.4f).toInt()
            }
        val (x1, y1, x2, y2) = when (direction) {
            "up" -> listOf(cx, cy - travel / 2, cx, cy + travel / 2)
            "down" -> listOf(cx, cy + travel / 2, cx, cy - travel / 2)
            "left" -> listOf(cx + travel / 2, cy, cx - travel / 2, cy)
            "right" -> listOf(cx - travel / 2, cy, cx + travel / 2, cy)
            else -> return null
        }
        val result = context.accessibility.swipe(x1, y1, x2, y2, 260)
        return if (result.isSuccess) travel else null
    }

    // -------------------------------------------------------------- wait/find

    private suspend fun waitAction(context: ToolContext, arguments: JsonObject): ToolResult {
        val query = (arguments["text"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'text' to wait for")
        val timeout = ((arguments["timeout_ms"] as? JsonPrimitive)?.intOrNull ?: 6_000)
            .coerceIn(500, 30_000)
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val match = uiBestMatches(context.accessibility.findText(query), query, limit = 1).firstOrNull()
            if (match != null) {
                return ToolResult.Success("'${match.text}' appeared at (${match.centerX}, ${match.centerY}).")
            }
            delay(POLL_INTERVAL_MS)
        }
        return ToolResult.Failure(notFoundMessage(context, query) + " (waited ${timeout}ms)")
    }

    private suspend fun findAction(context: ToolContext, arguments: JsonObject): ToolResult {
        val query = (arguments["text"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'text' to find")
        val treeMatches = uiBestMatches(context.accessibility.findText(query), query, limit = 10)
        if (treeMatches.isNotEmpty()) {
            return ToolResult.Success(renderMatches(query, treeMatches, source = "accessibility"))
        }
        val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
        val lines = context.ocr.recognize(bytes).getOrElse { error ->
            return ToolResult.Failure("OCR failed: ${error.message ?: "unknown error"}")
        }
        val ocrMatches = uiBestMatches(
            lines.map { TextNode(it.text, it.left, it.top, it.right, it.bottom) },
            query,
            limit = 10,
        )
        if (ocrMatches.isEmpty()) return ToolResult.Failure(notFoundMessage(context, query))
        return ToolResult.Success(renderMatches(query, ocrMatches, source = "OCR"))
    }

    private fun renderMatches(query: String, matches: List<TextNode>, source: String): String = buildString {
        append("Matches for '").append(query).append("' (").append(source).append(", best first):\n")
        matches.forEach { node ->
            append("\"").append(node.text).append("\" @ (").append(node.centerX)
                .append(", ").append(node.centerY).append(")\n")
        }
        append("Tip: action=tap with text=<match> resolves and taps it in one step.")
    }

    private suspend fun screenTextAction(context: ToolContext): ToolResult {
        val treeText = context.accessibility.screenText(150)
        if (treeText.isNotBlank()) {
            val clipped = treeText.take(SCREEN_TEXT_LIMIT)
            return ToolResult.Success("Screen text:\n$clipped")
        }
        val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
        val lines = context.ocr.recognize(bytes).getOrElse { error ->
            return ToolResult.Failure("OCR failed: ${error.message ?: "unknown error"}")
        }
        if (lines.isEmpty()) return ToolResult.Success("No text detected on screen.")
        val text = lines.joinToString("\n") { it.text }.take(SCREEN_TEXT_LIMIT)
        return ToolResult.Success("Screen text (OCR, ${lines.size} lines):\n$text")
    }

    private fun screenSignature(context: ToolContext): String {
        val pkg = context.accessibility.foregroundPackage() ?: "unknown"
        val title = context.accessibility.windowTitle()
        return if (title.isNullOrBlank()) pkg else "$pkg / $title"
    }

    private companion object {
        const val MAX_SCROLLS = 4
        const val SETTLE_DELAY_MS = 500L
        const val SCROLL_SETTLE_MS = 350L
        const val POLL_INTERVAL_MS = 400L
        const val SCREEN_TEXT_LIMIT = 6_000
    }
}

private fun accessibilityFailure(error: Throwable): ToolResult.Failure {
    val message = error.message.orEmpty()
    return if (message.contains("accessibility_not_enabled")) {
        ToolResult.Failure(
            "Accessibility service is not enabled. Ask the user to turn on " +
                "Settings → Accessibility → BAIC2 界面自动化, then retry.",
        )
    } else {
        ToolResult.Failure("UI automation failed: ${message.ifBlank { "unknown" }}")
    }
}
