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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.verlintas.baic2.core.data.repository.ScheduledTaskRepository
import com.verlintas.baic2.core.model.nextScheduledTrigger
import com.verlintas.baic2.tools.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Runs a scheduled prompt in the background and reports the result. */
@AndroidEntryPoint
class ScheduledRunService : Service() {

    @Inject
    lateinit var runner: ScheduledTaskRunner

    @Inject
    lateinit var scheduler: ScheduledTaskScheduler

    @Inject
    lateinit var repository: ScheduledTaskRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
        if (intent?.action != ACTION_RUN || taskId <= 0L) {
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        startForeground(
            RUNNING_NOTIFICATION_ID,
            notification(
                title = getString(R.string.scheduled_channel),
                text = getString(R.string.scheduled_running, ""),
                ongoing = true,
            ),
        )

        scope.launch {
            val task = repository.getById(taskId)
            if (task == null) {
                stopSelf()
                return@launch
            }
            val outcome = runCatching { runner.run(task) }.getOrElse { error ->
                ScheduledTaskRunner.Outcome(
                    conversationId = task.conversationId ?: 0L,
                    success = false,
                    summary = error.message ?: error.javaClass.simpleName,
                )
            }
            val now = System.currentTimeMillis()
            val next = nextScheduledTrigger(task.timeOfDay, task.daysOfWeek, now) ?: 0L
            repository.updateAfterRun(
                id = task.id,
                conversationId = outcome.conversationId.takeIf { it > 0L },
                lastRunAt = now,
                nextRunAt = next,
                lastResult = outcome.summary,
            )
            scheduler.schedule(task.copy(nextRunAt = next))

            val resultNotification = notification(
                title = getString(
                    if (outcome.success) R.string.scheduled_done else R.string.scheduled_failed,
                    task.name,
                ),
                text = outcome.summary,
                ongoing = false,
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(RESULT_NOTIFICATION_ID, resultNotification)
            ServiceCompat.stopForeground(this@ScheduledRunService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun notification(
        title: String,
        text: String,
        ongoing: Boolean,
    ): android.app.Notification {
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            PendingIntent.getActivity(
                this,
                2,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return android.app.Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setOngoing(ongoing)
            .setContentIntent(openIntent)
            .build()
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.scheduled_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    companion object {
        const val ACTION_RUN = "com.verlintas.baic2.scheduled.RUN"
        const val EXTRA_TASK_ID = "scheduledTaskId"
        private const val CHANNEL_ID = "baic2_scheduled"
        private const val RUNNING_NOTIFICATION_ID = 44
        private const val RESULT_NOTIFICATION_ID = 45
    }
}
