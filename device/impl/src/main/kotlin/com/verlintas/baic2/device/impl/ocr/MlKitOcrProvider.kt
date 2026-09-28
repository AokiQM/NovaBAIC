package com.verlintas.baic2.device.impl.ocr

import android.graphics.BitmapFactory
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.verlintas.baic2.device.api.OcrLine
import com.verlintas.baic2.device.api.OcrProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** ML Kit Chinese recognizer (also handles Latin text); works offline. */
@Singleton
class MlKitOcrProvider @Inject constructor() : OcrProvider {

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    override suspend fun recognize(image: ByteArray): Result<List<OcrLine>> =
        suspendCancellableCoroutine { continuation ->
            val bitmap = runCatching {
                BitmapFactory.decodeByteArray(image, 0, image.size)
            }.getOrNull()
            if (bitmap == null) {
                continuation.resume(Result.failure(IllegalArgumentException("undecodable_image")))
                return@suspendCancellableCoroutine
            }
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks.flatMap { block -> block.lines }.map { line ->
                        val bounds = line.boundingBox ?: Rect()
                        OcrLine(
                            text = line.text,
                            left = bounds.left,
                            top = bounds.top,
                            right = bounds.right,
                            bottom = bounds.bottom,
                        )
                    }
                    bitmap.recycle()
                    continuation.resume(Result.success(lines))
                }
                .addOnFailureListener { error ->
                    bitmap.recycle()
                    continuation.resume(Result.failure(error))
                }
        }
}
