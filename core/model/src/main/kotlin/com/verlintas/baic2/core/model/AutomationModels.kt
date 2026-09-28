package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class AutomationTrigger {
    TIME,
    BATTERY,
}

@Serializable
data class AutomationAction(
    val tool: String,
    val argsJson: String = "{}",
)

@Serializable
data class Automation(
    val id: Long = 0L,
    val name: String,
    val trigger: AutomationTrigger,
    /** "HH:mm" for TIME triggers. */
    val timeOfDay: String? = null,
    /** ISO weekdays 1..7 (Mon..Sun); null means every day. */
    val daysOfWeek: List<Int>? = null,
    /** Fire when battery drops below this percentage (BATTERY trigger). */
    val batteryBelow: Int? = null,
    val actions: List<AutomationAction>,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
) {
    fun scheduleLabel(): String = when (trigger) {
        AutomationTrigger.TIME -> {
            val days = daysOfWeek?.takeIf { it.isNotEmpty() }?.joinToString(",") { it.toString() } ?: "daily"
            "at ${timeOfDay ?: "?"} ($days)"
        }

        AutomationTrigger.BATTERY -> "battery < ${batteryBelow ?: 20}%"
    }
}
