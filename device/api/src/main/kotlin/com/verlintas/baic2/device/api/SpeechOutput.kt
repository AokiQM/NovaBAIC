package com.verlintas.baic2.device.api

/** Text-to-speech output for reading replies aloud. */
interface SpeechOutput {
    fun speak(text: String)

    fun stop()

    val isSpeaking: Boolean
}
