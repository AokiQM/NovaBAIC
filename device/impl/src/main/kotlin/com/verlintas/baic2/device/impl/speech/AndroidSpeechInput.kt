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

package com.verlintas.baic2.device.impl.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.verlintas.baic2.device.api.SpeechInputBridge
import com.verlintas.baic2.device.api.TranscriptionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One bounded utterance per call with an explicit recognizer lifecycle: the
 * recognizer is created, used and destroyed on the main thread, and a timeout
 * always releases it (no leaked microphone sessions).
 */
@Singleton
class AndroidSpeechInput @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechInputBridge {

    override suspend fun transcribe(durationMs: Long): TranscriptionResult =
        withContext(Dispatchers.Main.immediate) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext TranscriptionResult.Unavailable(
                    "RECORD_AUDIO permission not granted. Open Settings → Permissions → Microphone.",
                )
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return@withContext TranscriptionResult.Unavailable("Speech recognition is not available on this device")
            }

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            val deferred = CompletableDeferred<TranscriptionResult>()
            recognizer.setRecognitionListener(
                object : RecognitionListener {
                    override fun onResults(results: Bundle) {
                        val text = results
                            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                            .trim()
                        deferred.complete(
                            if (text.isBlank()) {
                                TranscriptionResult.Unavailable("Nothing was recognized")
                            } else {
                                TranscriptionResult.Text(text)
                            },
                        )
                    }

                    override fun onError(error: Int) {
                        deferred.complete(TranscriptionResult.Unavailable("Speech error $error"))
                    }

                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                },
            )

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }

            try {
                recognizer.startListening(intent)
                withTimeoutOrNull(durationMs) { deferred.await() } ?: TranscriptionResult.Timeout
            } finally {
                runCatching { recognizer.stopListening() }
                runCatching { recognizer.destroy() }
            }
        }
}
