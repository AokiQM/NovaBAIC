package com.verlintas.baic2.core.network.provider

import com.verlintas.baic2.core.model.ChatProvider
import com.verlintas.baic2.core.model.ProviderId
import com.verlintas.baic2.core.network.provider.anthropic.AnthropicProvider
import com.verlintas.baic2.core.network.provider.gemini.GeminiProvider
import com.verlintas.baic2.core.network.provider.openai.OpenAiCompatibleProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Singleton
class ProviderFactory @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    fun create(id: ProviderId): ChatProvider = when (id) {
        ProviderId.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(client, json)
        ProviderId.ANTHROPIC -> AnthropicProvider(client, json)
        ProviderId.GEMINI -> GeminiProvider(client, json)
    }
}
