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

/**
 * Curated metadata for well-known models. It gives the agent wizard sensible
 * defaults (model, temperature, max tokens, reasoning) and the context meter an
 * accurate window, without trusting whatever the gateway reports.
 *
 * Unknown models fall back to [ModelContextWindows]' pattern matching.
 */
data class ModelEntry(
    val id: String,
    val label: String,
    val provider: ProviderId,
    val family: String,
    val contextWindow: Long,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4_096,
    val supportsReasoning: Boolean = false,
)

object ModelCatalog {

    val entries: List<ModelEntry> = listOf(
        // OpenAI
        ModelEntry("gpt-5", "GPT-5", ProviderId.OPENAI_COMPATIBLE, "openai", 400_000),
        ModelEntry("gpt-5-mini", "GPT-5 mini", ProviderId.OPENAI_COMPATIBLE, "openai", 400_000),
        ModelEntry("gpt-4.1", "GPT-4.1", ProviderId.OPENAI_COMPATIBLE, "openai", 1_047_576),
        ModelEntry("gpt-4.1-mini", "GPT-4.1 mini", ProviderId.OPENAI_COMPATIBLE, "openai", 1_047_576),
        ModelEntry("gpt-4o", "GPT-4o", ProviderId.OPENAI_COMPATIBLE, "openai", 128_000),
        ModelEntry("gpt-4o-mini", "GPT-4o mini", ProviderId.OPENAI_COMPATIBLE, "openai", 128_000),
        ModelEntry("o3", "o3", ProviderId.OPENAI_COMPATIBLE, "openai", 200_000, supportsReasoning = true),
        ModelEntry("o4-mini", "o4-mini", ProviderId.OPENAI_COMPATIBLE, "openai", 200_000, supportsReasoning = true),

        // Anthropic
        ModelEntry("claude-sonnet-4-5", "Claude Sonnet 4.5", ProviderId.ANTHROPIC, "anthropic", 200_000, maxTokens = 8_192),
        ModelEntry("claude-opus-4-1", "Claude Opus 4.1", ProviderId.ANTHROPIC, "anthropic", 200_000, maxTokens = 8_192),
        ModelEntry("claude-haiku-4-5", "Claude Haiku 4.5", ProviderId.ANTHROPIC, "anthropic", 200_000, maxTokens = 8_192),
        ModelEntry("claude-3-7-sonnet", "Claude 3.7 Sonnet", ProviderId.ANTHROPIC, "anthropic", 200_000, maxTokens = 8_192),

        // Gemini
        ModelEntry("gemini-2.5-pro", "Gemini 2.5 Pro", ProviderId.GEMINI, "gemini", 1_048_576),
        ModelEntry("gemini-2.5-flash", "Gemini 2.5 Flash", ProviderId.GEMINI, "gemini", 1_048_576),
        ModelEntry("gemini-2.0-flash", "Gemini 2.0 Flash", ProviderId.GEMINI, "gemini", 1_048_576),

        // DeepSeek
        ModelEntry("deepseek-chat", "DeepSeek Chat", ProviderId.OPENAI_COMPATIBLE, "deepseek", 64_000),
        ModelEntry("deepseek-reasoner", "DeepSeek Reasoner", ProviderId.OPENAI_COMPATIBLE, "deepseek", 64_000, supportsReasoning = true),

        // Qwen
        ModelEntry("qwen-max", "Qwen Max", ProviderId.OPENAI_COMPATIBLE, "qwen", 131_072, maxTokens = 8_192),
        ModelEntry("qwen-plus", "Qwen Plus", ProviderId.OPENAI_COMPATIBLE, "qwen", 131_072, maxTokens = 8_192),
        ModelEntry("qwen-turbo", "Qwen Turbo", ProviderId.OPENAI_COMPATIBLE, "qwen", 131_072, maxTokens = 8_192),

        // Moonshot / Kimi
        ModelEntry("moonshot-v1-8k", "Kimi 8K", ProviderId.OPENAI_COMPATIBLE, "moonshot", 8_192),
        ModelEntry("moonshot-v1-32k", "Kimi 32K", ProviderId.OPENAI_COMPATIBLE, "moonshot", 32_768),
        ModelEntry("moonshot-v1-128k", "Kimi 128K", ProviderId.OPENAI_COMPATIBLE, "moonshot", 131_072),
        ModelEntry("kimi-k2", "Kimi K2", ProviderId.OPENAI_COMPATIBLE, "moonshot", 131_072),

        // GLM
        ModelEntry("glm-4-plus", "GLM-4 Plus", ProviderId.OPENAI_COMPATIBLE, "glm", 131_072),
        ModelEntry("glm-4-air", "GLM-4 Air", ProviderId.OPENAI_COMPATIBLE, "glm", 131_072),
        ModelEntry("glm-4.5", "GLM-4.5", ProviderId.OPENAI_COMPATIBLE, "glm", 131_072),

        // MiniMax
        ModelEntry("minimax-text-01", "MiniMax Text 01", ProviderId.OPENAI_COMPATIBLE, "minimax", 192_000),
    )

    fun entryFor(provider: ProviderId, model: String): ModelEntry? {
        val normalized = model.lowercase()
        if (normalized.isBlank()) return null
        entries.firstOrNull { it.provider == provider && it.id == normalized }?.let { return it }
        entries.firstOrNull { it.provider == provider && normalized.startsWith(it.id) }?.let { return it }
        return entries.firstOrNull { it.id == normalized }
            ?: entries.firstOrNull { normalized.startsWith(it.id) }
    }

    fun modelsFor(provider: ProviderId, family: String? = null): List<ModelEntry> {
        val pool = entries.filter { it.provider == provider }
        if (family.isNullOrBlank()) return pool
        return pool.filter { it.family == family.lowercase() }
    }
}
