package com.verlintas.baic2.device.api

/** One recognized text line with its bounding box in screenshot pixels. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/** On-device text recognition (bundled ML Kit). */
interface OcrProvider {
    suspend fun recognize(image: ByteArray): Result<List<OcrLine>>

    suspend fun recognizeText(image: ByteArray): Result<String> =
        recognize(image).map { lines -> lines.joinToString("\n") { it.text } }
}
