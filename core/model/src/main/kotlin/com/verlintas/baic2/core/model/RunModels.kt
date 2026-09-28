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
)
