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
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.verlintas.baic2.device.api.OcrProvider
import com.verlintas.baic2.device.api.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rasterizes PDF pages with the platform [PdfRenderer] and reads them with the
 * bundled ML Kit OCR. No extra dependency, works for text and scanned PDFs.
 */
@Singleton
class AndroidPdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ocrProvider: OcrProvider,
) : PdfTextExtractor {

    override suspend fun extract(bytes: ByteArray, maxPages: Int): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File.createTempFile("baic2-doc", ".pdf", context.cacheDir)
                try {
                    file.writeBytes(bytes)
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            val pageCount = renderer.pageCount.coerceAtMost(maxPages.coerceAtLeast(1))
                            val text = StringBuilder()
                            for (index in 0 until pageCount) {
                                renderer.openPage(index).use { page ->
                                    val scale = (PAGE_MAX_DIMENSION.toFloat() /
                                        max(page.width, page.height).coerceAtLeast(1)).coerceAtMost(1f)
                                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    val recognized = ocrProvider.recognizeText(bitmap.toPng()).getOrNull().orEmpty()
                                    bitmap.recycle()
                                    if (recognized.isNotBlank()) {
                                        text.append("## Page ").append(index + 1).append('\n')
                                        text.append(recognized.trim()).append("\n\n")
                                    }
                                }
                            }
                            val result = text.toString().trim()
                            require(result.isNotEmpty()) { "no_text_found" }
                            result
                        }
                    }
                } finally {
                    file.delete()
                }
            }
        }

    private fun Bitmap.toPng(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

    private companion object {
        const val PAGE_MAX_DIMENSION = 1_600
    }
}
