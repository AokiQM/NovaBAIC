package com.verlintas.baic2.tools.personal

import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.device.api.ReminderScheduler
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One-shot and daily reminders (the old set_alarm + schedule_repeat merged).
 * Provide `delay_minutes`, `at_epoch_ms`, or `daily_at` ("HH:mm").
 */
class ReminderTool(
    private val scheduler: ReminderScheduler,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "reminder",
        description = "Set a reminder. Use delay_minutes, at_epoch_ms, or daily_at=\"HH:mm\" for a daily repeat.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"},"delay_minutes":{"type":"integer"},"at_epoch_ms":{"type":"integer"},"daily_at":{"type":"string","description":"HH:mm"}},"required":["text"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'text' argument")
        val dailyAt = (arguments["daily_at"] as? JsonPrimitive)?.contentOrNull?.trim()
        val epoch = (arguments["at_epoch_ms"] as? JsonPrimitive)?.longOrNull
        val delayMinutes = (arguments["delay_minutes"] as? JsonPrimitive)?.intOrNull

        val repeatDaily = !dailyAt.isNullOrBlank() ||
            ((arguments["repeat_daily"] as? JsonPrimitive)?.booleanOrNull == true)

        val triggerAt = when {
            !dailyAt.isNullOrBlank() -> {
                val parts = dailyAt.split(":")
                if (parts.size != 2) {
                    return ToolResult.Failure("daily_at must look like \"21:30\"")
                }
                val hour = parts[0].toIntOrNull()
                val minute = parts[1].toIntOrNull()
                if (hour == null || minute == null) {
                    return ToolResult.Failure("daily_at must look like \"21:30\"")
                }
                scheduler.nextDailyOccurrence(hour, minute)
            }

            epoch != null -> epoch
            delayMinutes != null -> System.currentTimeMillis() + delayMinutes.coerceIn(1, 43_200) * 60_000L
            else -> return ToolResult.Failure("Provide delay_minutes, at_epoch_ms, or daily_at.")
        }

        return try {
            scheduler.schedule(text, triggerAt, repeatDaily)
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            ToolResult.Success(
                "Reminder set for ${format.format(Date(triggerAt))}" +
                    if (repeatDaily) " (repeats daily)" else "",
            )
        } catch (e: Exception) {
            ToolResult.Failure("Could not schedule the reminder: ${e.message}")
        }
    }
}
