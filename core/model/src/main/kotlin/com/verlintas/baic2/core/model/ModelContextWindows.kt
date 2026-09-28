package com.verlintas.baic2.core.model

/**
 * Best-effort context window lookup for usage display and auto-compression.
 * Unknown models return null: the UI then shows raw token counts and skips
 * percentage-based decisions.
 */
object ModelContextWindows {

    private val patterns = listOf(
        "claude" to 200_000L,
        "gemini" to 1_000_000L,
        "deepseek" to 64_000L,
        "qwen" to 128_000L,
        "moonshot" to 128_000L,
        "kimi" to 128_000L,
        "glm" to 128_000L,
        "gpt-4o" to 128_000L,
        "gpt-4.1" to 128_000L,
        "gpt-5" to 128_000L,
        "o3" to 200_000L,
        "o4" to 200_000L,
        "llama" to 128_000L,
        "mistral" to 128_000L,
        "minimax" to 192_000L,
        "step" to 128_000L,
    )

    fun forModel(model: String): Long? {
        val normalized = model.lowercase()
        return patterns.firstOrNull { normalized.contains(it.first) }?.second
    }
}
