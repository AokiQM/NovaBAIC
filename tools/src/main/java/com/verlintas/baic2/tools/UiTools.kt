package com.verlintas.baic2.tools

import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

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

/** Finds elements via the accessibility tree first, OCR as fallback. */
class UiFindTool : DeviceTool {
    override val spec = ToolSpec(
        name = "ui_find",
        description = "Find on-screen elements whose text matches the query and return tap coordinates.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val query = (arguments["text"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'text' argument")

        val nodes = context.accessibility.findText(query)
        if (nodes.isNotEmpty()) {
            return ToolResult.Success(
                buildString {
                    append("Found ${nodes.size} match(es) for '$query' (accessibility tree):\n")
                    nodes.take(10).forEach { node ->
                        append("\"${node.text}\" @ (${node.centerX}, ${node.centerY})\n")
                    }
                },
            )
        }

        val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
        val lines = context.ocr.recognize(bytes).getOrElse { error ->
            return ToolResult.Failure("OCR failed: ${error.message ?: "unknown error"}")
        }
        val matches = lines.filter { it.text.contains(query, ignoreCase = true) }
        if (matches.isEmpty()) {
            return ToolResult.Failure("No on-screen element matches '$query'. Use screen_ocr to list visible text.")
        }
        return ToolResult.Success(
            buildString {
                append("Found ${matches.size} match(es) for '$query' (OCR):\n")
                matches.take(10).forEach { line ->
                    append("\"${line.text}\" @ (${line.centerX}, ${line.centerY})\n")
                }
            },
        )
    }
}

class UiTapTool : DeviceTool {
    override val spec = ToolSpec(
        name = "ui_tap",
        description = "Tap an element by its text, or by explicit screen coordinates.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"},"x":{"type":"integer"},"y":{"type":"integer"}}}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
        val x = (arguments["x"] as? JsonPrimitive)?.intOrNull
        val y = (arguments["y"] as? JsonPrimitive)?.intOrNull

        val target = when {
            text != null -> {
                val nodes = context.accessibility.findText(text)
                val node = nodes.firstOrNull()
                if (node != null) {
                    node.centerX to node.centerY
                } else {
                    val bytes = context.screenshot.capture().getOrElse { return screenshotFailure(it) }
                    val line = context.ocr.recognize(bytes).getOrNull()
                        ?.firstOrNull { it.text.contains(text, ignoreCase = true) }
                        ?: return ToolResult.Failure(
                            "No on-screen element matches '$text'. Use ui_find to inspect the screen.",
                        )
                    line.centerX to line.centerY
                }
            }

            x != null && y != null -> x to y
            else -> return ToolResult.Failure("Provide 'text' or both 'x' and 'y'")
        }

        return context.accessibility.tap(target.first, target.second).fold(
            onSuccess = { ToolResult.Success("Tapped (${target.first}, ${target.second})") },
            onFailure = { accessibilityFailure(it) },
        )
    }
}

class UiSwipeTool : DeviceTool {
    override val spec = ToolSpec(
        name = "ui_swipe",
        description = "Swipe between two screen points (e.g. scroll a list).",
        parametersJson = """{"type":"object","properties":{"x1":{"type":"integer"},"y1":{"type":"integer"},"x2":{"type":"integer"},"y2":{"type":"integer"},"duration_ms":{"type":"integer"}},"required":["x1","y1","x2","y2"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        fun value(name: String): Int? = (arguments[name] as? JsonPrimitive)?.intOrNull
        val x1 = value("x1") ?: return ToolResult.Failure("Missing 'x1'")
        val y1 = value("y1") ?: return ToolResult.Failure("Missing 'y1'")
        val x2 = value("x2") ?: return ToolResult.Failure("Missing 'x2'")
        val y2 = value("y2") ?: return ToolResult.Failure("Missing 'y2'")
        val duration = value("duration_ms") ?: 300

        return context.accessibility.swipe(x1, y1, x2, y2, duration).fold(
            onSuccess = { ToolResult.Success("Swiped ($x1, $y1) → ($x2, $y2)") },
            onFailure = { accessibilityFailure(it) },
        )
    }
}

class UiTypeTool : DeviceTool {
    override val spec = ToolSpec(
        name = "ui_type",
        description = "Type text into the currently focused input field.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'text' argument")
        return context.accessibility.typeText(text).fold(
            onSuccess = { ToolResult.Success("Typed ${text.length} characters.") },
            onFailure = { accessibilityFailure(it) },
        )
    }
}

class UiPressTool : DeviceTool {
    override val spec = ToolSpec(
        name = "ui_press",
        description = "Press a system key: back, home, recents or notifications.",
        parametersJson = """{"type":"object","properties":{"key":{"type":"string"}},"required":["key"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val key = (arguments["key"] as? JsonPrimitive)?.content?.lowercase()
            ?: return ToolResult.Failure("Missing 'key' argument")
        return context.accessibility.pressKey(key).fold(
            onSuccess = { ToolResult.Success("Pressed '$key'") },
            onFailure = { accessibilityFailure(it) },
        )
    }
}
