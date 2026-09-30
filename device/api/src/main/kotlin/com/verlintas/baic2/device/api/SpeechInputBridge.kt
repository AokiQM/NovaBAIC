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

package com.verlintas.baic2.device.api

import kotlinx.coroutines.flow.Flow

sealed interface TranscriptionResult {
    data class Text(val value: String) : TranscriptionResult

    data object Timeout : TranscriptionResult

    data class Unavailable(val reason: String) : TranscriptionResult
}

/** Why a listening session ended without usable text. */
enum class SpeechFailure {
    NO_MATCH,
    TIMEOUT,
    BUSY,
    PERMISSION,
    NO_SERVICE,
    NETWORK,
    UNKNOWN,
}

sealed interface SpeechSessionEvent {
    data object Ready : SpeechSessionEvent

    /** Live transcript, replaces the previous partial. */
    data class Partial(val text: String) : SpeechSessionEvent

    /** Input loudness, normalized to 0..1 (for waveforms). */
    data class Level(val level: Float) : SpeechSessionEvent

    data class Final(val text: String) : SpeechSessionEvent

    data class Error(val failure: SpeechFailure) : SpeechSessionEvent
}

/**
 * One live dictation session. The implementation watches for silence and
 * finalizes on its own; callers only need [stop] (finish now) or [cancel]
 * (discard).
 */
interface SpeechSession {
    val events: Flow<SpeechSessionEvent>
    fun stop()
    fun cancel()
}

/** Records spoken utterances and returns transcriptions. */
interface SpeechInputBridge {
    suspend fun transcribe(durationMs: Long = 8_000): TranscriptionResult

    /** True when an on-device recognition service exists. */
    fun isAvailable(): Boolean

    /**
     * Starts a live session, or returns null when recognition is unavailable
     * or the microphone permission is missing.
     */
    fun startSession(languageTag: String? = null): SpeechSession?
}
