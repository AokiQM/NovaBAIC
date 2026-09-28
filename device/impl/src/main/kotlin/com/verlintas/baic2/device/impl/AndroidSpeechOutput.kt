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

package com.verlintas.baic2.device.impl

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.verlintas.baic2.device.api.SpeechOutput
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSpeechOutput @Inject constructor(
    @ApplicationContext context: Context,
) : SpeechOutput {

    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) {
            ready.set(true)
            // Best effort: prefer the system language, fall back silently.
            runCatching {
                val result = tts.setLanguage(Locale.getDefault())
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
            }
        }
    }.apply {
        setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    speaking.set(true)
                }

                override fun onDone(utteranceId: String?) {
                    speaking.set(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    speaking.set(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    speaking.set(false)
                }
            },
        )
    }

    override fun speak(text: String) {
        if (!ready.get() || text.isBlank()) return
        stop()
        speaking.set(true)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        if (ready.get() && tts.isSpeaking) {
            tts.stop()
        }
        speaking.set(false)
    }

    override val isSpeaking: Boolean get() = speaking.get()
}
