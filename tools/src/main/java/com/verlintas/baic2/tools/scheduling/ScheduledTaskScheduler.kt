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

package com.verlintas.baic2.tools.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.verlintas.baic2.core.data.repository.ScheduledTaskControl
import com.verlintas.baic2.core.data.repository.ScheduledTaskRepository
import com.verlintas.baic2.core.model.ScheduledTask
import com.verlintas.baic2.core.model.nextScheduledTrigger
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exact alarms for scheduled prompts. `setAlarmClock` is used deliberately:
 * it survives Doze and grants the foreground-service start exemption the
 * background agent run needs on Android 12+.
 */
@Singleton
class ScheduledTaskScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScheduledTaskRepository,
) : ScheduledTaskControl {

    override fun schedule(task: ScheduledTask) {
        cancel(task.id)
        if (!task.enabled) return
        val triggerAt = nextScheduledTrigger(task.timeOfDay, task.daysOfWeek, System.currentTimeMillis())
            ?: return
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarm = AlarmManager.AlarmClockInfo(triggerAt, showIntent(task.id))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            runCatching { manager.setAlarmClock(alarm, pendingIntent(task.id)) }
                .onFailure { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(task.id)) }
        } else {
            manager.setAlarmClock(alarm, pendingIntent(task.id))
        }
    }

    override fun cancel(id: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.cancel(pendingIntent(id))
    }

    /** Starts the run immediately (the editor's "run now" and testing). */
    override fun runNow(id: Long) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, ScheduledRunService::class.java)
                .setAction(ScheduledRunService.ACTION_RUN)
                .putExtra(ScheduledRunService.EXTRA_TASK_ID, id),
        )
    }

    override suspend fun rescheduleAll() {
        repository.getEnabled().forEach(::schedule)
    }

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt() and 0x0FFFFFFF,
        Intent(context, ScheduledTaskAlarmReceiver::class.java)
            .putExtra(ScheduledTaskAlarmReceiver.EXTRA_TASK_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun showIntent(id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt() and 0x0FFFFFFF,
            context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

/** Fires at the scheduled instant and hands the work to the run service. */
@AndroidEntryPoint
class ScheduledTaskAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id <= 0L) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, ScheduledRunService::class.java)
                .setAction(ScheduledRunService.ACTION_RUN)
                .putExtra(ScheduledRunService.EXTRA_TASK_ID, id),
        )
    }

    companion object {
        const val EXTRA_TASK_ID = "scheduledTaskId"
    }
}

/** Re-arms every enabled scheduled task after a reboot. */
@AndroidEntryPoint
class ScheduledTaskBootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: ScheduledTaskScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
