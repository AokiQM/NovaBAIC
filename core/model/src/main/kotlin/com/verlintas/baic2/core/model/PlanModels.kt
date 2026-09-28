package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlanStepStatus {
    PENDING,
    DOING,
    DONE,
    FAILED,
}

@Serializable
data class PlanStep(
    val title: String,
    val status: PlanStepStatus = PlanStepStatus.PENDING,
)

/** The model-maintained task plan for a conversation (a first-class artifact). */
@Serializable
data class Plan(
    val conversationId: Long,
    val steps: List<PlanStep>,
    val updatedAt: Long,
) {
    val doneCount: Int get() = steps.count { it.status == PlanStepStatus.DONE }

    fun render(): String = buildString {
        append("Progress: $doneCount/${steps.size}")
        steps.forEachIndexed { index, step ->
            val marker = when (step.status) {
                PlanStepStatus.PENDING -> "[ ]"
                PlanStepStatus.DOING -> "[>]"
                PlanStepStatus.DONE -> "[x]"
                PlanStepStatus.FAILED -> "[!]"
            }
            append('\n').append(marker).append(' ').append(index + 1).append(". ").append(step.title)
        }
    }
}

data class RunSummary(
    val id: Long,
    val conversationId: Long,
    val conversationTitle: String,
    val mode: AppMode,
    val state: RunState,
    val startedAt: Long,
    val updatedAt: Long,
)
