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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.verlintas.baic2.device.api.SpeechFailure
import com.verlintas.baic2.device.api.SpeechInputBridge
import com.verlintas.baic2.device.api.SpeechSession
import com.verlintas.baic2.device.api.SpeechSessionEvent
import com.verlintas.baic2.device.api.TranscriptionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * In-app speech recognition. Every session owns an explicit recognizer
 * lifecycle (created, used and destroyed on the main thread) and watches for
 * silence so a caller never needs to guess when the user stopped talking.
 */
@Singleton
class AndroidSpeechInput @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechInputBridge {

    override fun isAvailable(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED &&
            SpeechRecognizer.isRecognitionAvailable(context)

    override fun startSession(languageTag: String?): SpeechSession? {
        if (!isAvailable()) return null
        return AndroidSpeechSession(context, languageTag)
    }

    override suspend fun transcribe(durationMs: Long): TranscriptionResult =
        withContext(Dispatchers.Main.immediate) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext TranscriptionResult.Unavailable(permissionMessage())
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                return@withContext TranscriptionResult.Unavailable(noServiceMessage())
            }
            val session = AndroidSpeechSession(context, languageTag = null)
            try {
                val event = withTimeoutOrNull(durationMs) {
                    session.events.first {
                        it is SpeechSessionEvent.Final || it is SpeechSessionEvent.Error
                    }
                }
                when (event) {
                    is SpeechSessionEvent.Final -> TranscriptionResult.Text(event.text)
                    is SpeechSessionEvent.Error -> TranscriptionResult.Unavailable(
                        event.failure.message(),
                    )

                    else -> TranscriptionResult.Timeout
                }
            } finally {
                session.cancel()
            }
        }
}

private class AndroidSpeechSession(
    private val context: Context,
    languageTag: String?,
) : SpeechSession {

    private val _events = MutableSharedFlow<SpeechSessionEvent>(extraBufferCapacity = 64)
    override val events: Flow<SpeechSessionEvent> = _events.asSharedFlow()

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val finished = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)

    private var recognizer: SpeechRecognizer? = null
    private var watcher: Job? = null
    private var bestPartial: String = ""
    private var lastSpeechAt: Long = 0
    private var stopRequestedAt: Long = 0

    private val startedAt = SystemClock.elapsedRealtime()

    init {
        main.post { start(languageTag) }
    }

    override fun stop() {
        if (finished.get() || !stopRequested.compareAndSet(false, true)) return
        main.post {
            stopRequestedAt = SystemClock.elapsedRealtime()
            runCatching { recognizer?.stopListening() }
        }
    }

    override fun cancel() {
        if (!finished.compareAndSet(false, true)) return
        main.post { teardown() }
    }

    private fun start(languageTag: String?) {
        val created = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }
            .getOrNull()
        if (created == null) {
            finishWithError(SpeechFailure.NO_SERVICE)
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                languageTag ?: Locale.getDefault().toLanguageTag(),
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        runCatching { created.startListening(intent) }.onFailure {
            finishWithError(SpeechFailure.UNKNOWN)
            return
        }
        watcher = scope.launch {
            while (isActive && !finished.get()) {
                delay(WATCH_INTERVAL_MS)
                val now = SystemClock.elapsedRealtime()
                val heardSpeech = lastSpeechAt > 0
                when {
                    now - startedAt > MAX_SESSION_MS ->
                        finishWithBestOr(SpeechFailure.TIMEOUT)

                    heardSpeech && now - lastSpeechAt > SILENCE_MS -> {
                        runCatching { recognizer?.stopListening() }
                        if (now - lastSpeechAt > SILENCE_MS + FINALIZE_GRACE_MS) {
                            finishWithBestOr(SpeechFailure.TIMEOUT)
                        }
                    }

                    !heardSpeech && now - startedAt > NO_SPEECH_MS ->
                        finishWithError(SpeechFailure.NO_MATCH)

                    stopRequested.get() && stopRequestedAt > 0 &&
                        now - stopRequestedAt > STOP_GRACE_MS &&
                        now - maxOf(lastSpeechAt, stopRequestedAt) > STOP_GRACE_MS ->
                        finishWithBestOr(SpeechFailure.NO_MATCH)
                }
            }
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _events.tryEmit(SpeechSessionEvent.Ready)
        }

        override fun onRmsChanged(rmsdB: Float) {
            _events.tryEmit(SpeechSessionEvent.Level(normalize(rmsdB)))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotBlank()) {
                bestPartial = text
                lastSpeechAt = SystemClock.elapsedRealtime()
                _events.tryEmit(SpeechSessionEvent.Partial(text))
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            val finalText = text.ifBlank { bestPartial }
            if (finalText.isBlank()) {
                finishWithError(SpeechFailure.NO_MATCH)
            } else {
                finishWith(SpeechSessionEvent.Final(finalText))
            }
        }

        override fun onError(error: Int) {
            if (error == SpeechRecognizer.ERROR_NO_MATCH && bestPartial.isNotBlank()) {
                finishWith(SpeechSessionEvent.Final(bestPartial))
                return
            }
            finishWithError(error.toFailure())
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun finishWithBestOr(failure: SpeechFailure) {
        if (bestPartial.isNotBlank()) {
            finishWith(SpeechSessionEvent.Final(bestPartial))
        } else {
            finishWithError(failure)
        }
    }

    private fun finishWith(event: SpeechSessionEvent) {
        if (!finished.compareAndSet(false, true)) return
        _events.tryEmit(event)
        main.post { teardown() }
    }

    private fun finishWithError(failure: SpeechFailure) {
        if (!finished.compareAndSet(false, true)) return
        _events.tryEmit(SpeechSessionEvent.Error(failure))
        main.post { teardown() }
    }

    private fun teardown() {
        watcher?.cancel()
        watcher = null
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        scope.cancel()
    }

    private fun normalize(rmsdB: Float): Float = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)

    private companion object {
        const val WATCH_INTERVAL_MS = 150L
        const val SILENCE_MS = 1_400L
        const val FINALIZE_GRACE_MS = 900L
        const val NO_SPEECH_MS = 6_000L
        const val MAX_SESSION_MS = 60_000L
        const val STOP_GRACE_MS = 900L
    }
}

private fun Int.toFailure(): SpeechFailure = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NO_MATCH
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.BUSY
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PERMISSION
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechFailure.NETWORK
    SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER -> SpeechFailure.NO_SERVICE
    else -> SpeechFailure.UNKNOWN
}

internal fun SpeechFailure.message(): String = when (this) {
    SpeechFailure.NO_MATCH, SpeechFailure.TIMEOUT -> "Nothing was recognized"
    SpeechFailure.BUSY -> "The speech recognizer is busy; try again in a moment"
    SpeechFailure.PERMISSION -> permissionMessage()
    SpeechFailure.NO_SERVICE -> noServiceMessage()
    SpeechFailure.NETWORK -> "Speech recognition needs a network connection"
    SpeechFailure.UNKNOWN -> "Speech recognition failed"
}

private fun permissionMessage(): String =
    "RECORD_AUDIO permission not granted. Open Settings → Permissions → Microphone."

private fun noServiceMessage(): String =
    "Speech recognition is not available on this device"

private fun CoroutineScope.cancel() {
    coroutineContext[Job]?.cancel()
}
