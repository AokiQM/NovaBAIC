package com.verlintas.baic2.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderId {
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    GEMINI,
}

/**
 * Persisted agent configuration. The API key is never part of this model:
 * it is stored separately in encrypted form and only decrypted into
 * [ProviderConfig] at request time.
 */
@Serializable
data class Agent(
    val id: Long = 0L,
    val name: String,
    val provider: ProviderId = ProviderId.OPENAI_COMPATIBLE,
    val baseUrl: String,
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val reasoning: Boolean = false,
    val systemPrompt: String = "",
    val isDefault: Boolean = false,
)

/**
 * Runtime configuration including the plaintext API key. Never persisted,
 * never logged.
 */
data class ProviderConfig(
    val provider: ProviderId,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int? = null,
    val reasoning: Boolean = false,
)
