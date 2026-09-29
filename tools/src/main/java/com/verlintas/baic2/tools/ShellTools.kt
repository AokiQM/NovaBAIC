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
import com.verlintas.baic2.device.api.ShellBridge
import com.verlintas.baic2.device.api.ShellResult
import com.verlintas.baic2.tools.apps.AppResolver
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Root-level shell through Shizuku with a bounded process lifecycle. */
class RunShellTool(
    private val shell: ShellBridge,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "run_shell",
        description = "Run a shell command with Shizuku (ADB-level privileges). Returns stdout, " +
            "stderr and the exit code. Requires Shizuku; prefer a dedicated tool when one exists.",
        parametersJson = """{"type":"object","properties":{"command":{"type":"string"},"timeout_seconds":{"type":"integer","description":"1-120, default 30"}},"required":["command"]}""",
        readOnly = false,
        danger = DangerLevel.HIGH,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val command = (arguments["command"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (command.isBlank()) return ToolResult.Failure("Missing 'command' argument")
        if (command.length > MAX_COMMAND_CHARS) {
            return ToolResult.Failure("Command too long (max $MAX_COMMAND_CHARS characters)")
        }
        val timeoutSeconds = ((arguments["timeout_seconds"] as? JsonPrimitive)?.intOrNull ?: 30)
            .coerceIn(1, 120)
        return when (val result = shell.exec(command, timeoutSeconds * 1_000L)) {
            is ShellResult.Unavailable ->
                ToolResult.Failure("Shizuku unavailable — ${result.reason}")

            is ShellResult.Timeout ->
                ToolResult.Failure(
                    "ERROR: command timed out after ${timeoutSeconds}s." +
                        if (result.partialOutput.isBlank()) {
                            ""
                        } else {
                            "\nPartial output:\n${result.partialOutput.take(2_000)}"
                        },
                )

            is ShellResult.Output -> {
                val text = buildString {
                    if (result.stdout.isNotBlank()) append(result.stdout)
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append("stderr:\n").append(result.stderr)
                    }
                    if (isEmpty()) append("(no output)")
                    append("\n(exit ").append(result.exitCode)
                    if (result.truncated) append(", output truncated")
                    append(')')
                }
                if (result.exitCode == 0) ToolResult.Success(text) else ToolResult.Failure(text)
            }
        }
    }

    private companion object {
        const val MAX_COMMAND_CHARS = 2_000
    }
}

/** App management through Shizuku with strict package-name validation. */
class ManageAppTool(
    private val shell: ShellBridge,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "manage_app",
        description = "Manage installed apps via Shizuku: 'list' (optional keyword), 'info', " +
            "'force_stop', 'clear_data', 'uninstall'. Targets accept a package name OR an app " +
            "label (resolved fuzzily); the resolved package is validated before use.",
        parametersJson = """{"type":"object","properties":{"action":{"type":"string","enum":["list","info","force_stop","clear_data","uninstall"]},"app":{"type":"string","description":"app label or package name, required unless action=list"},"keyword":{"type":"string","description":"filter for action=list"}},"required":["action"]}""",
        readOnly = false,
        danger = DangerLevel.HIGH,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = (arguments["action"] as? JsonPrimitive)?.content?.trim().orEmpty()
        val keyword = (arguments["keyword"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (action == "list") {
            val command = if (keyword.isBlank()) {
                "pm list packages -3 | head -100"
            } else {
                "pm list packages | grep -i -- ${shellQuote(keyword)} | head -100"
            }
            return runShell(command)
        }

        val rawTarget = (arguments["app"] as? JsonPrimitive)?.content?.trim()
            ?: (arguments["package"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'app' (label or package name)")
        val packageName = resolvePackage(context, rawTarget)
            ?: return ToolResult.Failure(
                "No installed app matches '$rawTarget'. Use list_installed_apps to see what is installed.",
            )
        if (!PACKAGE_PATTERN.matches(packageName)) {
            return ToolResult.Failure(
                "Invalid package name '$packageName' — expected letters, digits, dots and underscores",
            )
        }
        val command = when (action) {
            "info" -> "dumpsys package $packageName | grep -E 'versionName|firstInstallTime|lastUpdateTime' | head -6"
            "force_stop" -> "am force-stop $packageName && echo stopped $packageName"
            "clear_data" -> "pm clear $packageName"
            "uninstall" -> "pm uninstall $packageName"
            else -> return ToolResult.Failure(
                "ERROR: unknown action '$action' — use list/info/force_stop/clear_data/uninstall",
            )
        }
        return runShell(command)
    }

    private suspend fun runShell(command: String): ToolResult =
        when (val result = shell.exec(command, 30_000L)) {
            is ShellResult.Unavailable -> ToolResult.Failure("Shizuku unavailable — ${result.reason}")
            is ShellResult.Timeout -> ToolResult.Failure("command timed out")
            is ShellResult.Output -> {
                val text = buildString {
                    if (result.stdout.isNotBlank()) append(result.stdout)
                    if (result.stderr.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append("stderr:\n").append(result.stderr)
                    }
                    if (isEmpty()) append("(no output)")
                    append("\n(exit ").append(result.exitCode).append(')')
                }
                if (result.exitCode == 0) ToolResult.Success(text) else ToolResult.Failure(text)
            }
        }

    /** Package names pass through; anything else is treated as a fuzzy app label. */
    private fun resolvePackage(context: ToolContext, target: String): String? {
        if (PACKAGE_PATTERN.matches(target) && target.contains('.')) return target
        return AppResolver.resolve(context.appContext, target).firstOrNull()?.packageName
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        val PACKAGE_PATTERN = Regex("[a-zA-Z0-9._]{2,200}")
    }
}
