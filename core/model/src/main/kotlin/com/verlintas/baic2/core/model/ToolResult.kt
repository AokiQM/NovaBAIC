package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

/**
 * Result of a single tool invocation.
 *
 * Failures always carry an actionable reason that is written for the model:
 * a failure with a next step ends retry loops, a bare failure starts them.
 */
@Serializable
sealed interface ToolResult {

    @Serializable
    data class Success(val output: String) : ToolResult

    @Serializable
    data class Failure(
        val reason: String,
        val recoverable: Boolean = true,
    ) : ToolResult

    @Serializable
    data class Denied(val reason: String) : ToolResult
}
