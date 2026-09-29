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

package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RunBudget(
    val maxRounds: Int,
    val maxToolCalls: Int,
    val maxWallClockMs: Long,
) {
    companion object {
        fun forMode(mode: AppMode): RunBudget = when (mode) {
            AppMode.CHAT -> RunBudget(maxRounds = 1, maxToolCalls = 0, maxWallClockMs = 120_000)
            AppMode.CHAT_PLUS -> RunBudget(maxRounds = 6, maxToolCalls = 12, maxWallClockMs = 300_000)
            AppMode.ACT -> RunBudget(maxRounds = 12, maxToolCalls = 32, maxWallClockMs = 600_000)
            AppMode.MAX -> RunBudget(maxRounds = 50, maxToolCalls = 120, maxWallClockMs = 1_800_000)
        }
    }
}

@Serializable
enum class RunState {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class Run(
    val id: Long = 0L,
    val conversationId: Long,
    val mode: AppMode,
    val state: RunState = RunState.RUNNING,
    val startedAt: Long,
    val updatedAt: Long,
    val roundsUsed: Int = 0,
    val toolCallsUsed: Int = 0,
)
