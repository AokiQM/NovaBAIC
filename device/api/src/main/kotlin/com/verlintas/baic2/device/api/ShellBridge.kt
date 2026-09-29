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

package com.verlintas.baic2.device.api

import kotlinx.coroutines.flow.StateFlow

/** Shizuku lifecycle, surfaced in the permission center and to shell tools. */
sealed interface ShellState {
    /** Shizuku app missing or its service is not running. */
    data object Unavailable : ShellState

    /** Shizuku is running but this app was not granted access yet. */
    data object PermissionRequired : ShellState

    data object Ready : ShellState
}

sealed interface ShellResult {
    data class Output(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val truncated: Boolean,
    ) : ShellResult

    data class Unavailable(val reason: String) : ShellResult

    data class Timeout(val partialOutput: String) : ShellResult
}

/**
 * Runs shell commands through Shizuku with an explicit process lifecycle:
 * bounded wall-clock time, streams drained concurrently, process destroyed on
 * timeout and output capped before it reaches the model.
 */
interface ShellBridge {
    val state: StateFlow<ShellState>

    /** Asks the Shizuku manager app for the API permission. */
    fun requestPermission()

    suspend fun exec(
        command: String,
        timeoutMs: Long = 30_000,
        maxOutputChars: Int = 8_000,
    ): ShellResult
}
