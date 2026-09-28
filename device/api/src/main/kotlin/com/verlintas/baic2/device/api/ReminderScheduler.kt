package com.verlintas.baic2.device.api

/** Schedules user-visible reminders through AlarmManager. */
interface ReminderScheduler {
    fun canScheduleExact(): Boolean

    /** Returns the trigger time actually used. */
    fun schedule(text: String, triggerAtMillis: Long, repeatDaily: Boolean): Long

    fun nextDailyOccurrence(hour: Int, minute: Int): Long
}
