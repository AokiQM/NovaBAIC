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
