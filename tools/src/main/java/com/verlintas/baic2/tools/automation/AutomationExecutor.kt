package com.verlintas.baic2.tools.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.verlintas.baic2.core.data.repository.AutomationRepository
import com.verlintas.baic2.core.engine.AgentLoop
import com.verlintas.baic2.core.engine.ToolRunner
import com.verlintas.baic2.core.model.Automation
import com.verlintas.baic2.core.model.ToolCall
import com.verlintas.baic2.core.model.ToolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Runs an automation's tool sequence and reports the outcome. */
@Singleton
class AutomationExecutor @Inject constructor(
    private val toolRunner: ToolRunner,
    private val automationRepository: AutomationRepository,
    @ApplicationContext private val context: Context,
) {

    suspend fun run(automation: Automation): String {
        val lines = mutableListOf<String>()
        var failures = 0
        automation.actions.forEachIndexed { index, action ->
            val call = ToolCall(
                id = "automation_${automation.id}_$index",
                name = action.tool,
                argumentsJson = action.argsJson,
            )
            val result = try {
                toolRunner.run(call, com.verlintas.baic2.core.engine.ToolRunContext(null, com.verlintas.baic2.core.model.AppMode.MAX))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ToolResult.Failure(e.message ?: "crashed")
            }
            when (result) {
                is ToolResult.Success -> lines += "✓ ${action.tool}: ${result.output.take(120)}"
                is ToolResult.Failure -> {
                    failures++
                    lines += "✗ ${action.tool}: ${result.reason}"
                }
                is ToolResult.Denied -> {
                    failures++
                    lines += "⊘ ${action.tool}: ${result.reason}"
                }
            }
        }
        notify(automation, lines, failures)
        return lines.joinToString("\n")
    }

    private fun notify(automation: Automation, lines: List<String>, failures: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Automations", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val title = if (failures == 0) "自动化完成：${automation.name}" else "自动化部分失败：${automation.name}"
        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(android.app.Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .build()
        manager.notify(automation.id.toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "baic2_automations"
    }
}
