package com.verlintas.baic2.tools.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import java.io.File
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

class OcrFileTool @Inject constructor() : DeviceTool {

    override val spec = ToolSpec(
        name = "ocr_file",
        description = "Run on-device OCR on an image file in Downloads/Documents and return its text.",
        parametersJson = """{"type":"object","properties":{"file_name":{"type":"string"}},"required":["file_name"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val name = (arguments["file_name"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'file_name' argument")
        val collection = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND " +
            "${MediaStore.MediaColumns.MIME_TYPE} LIKE 'image/%'"
        val uri = context.appContext.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            arrayOf(name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.net.Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            } else {
                null
            }
        } ?: return ToolResult.Failure("Image '$name' not found. Use list_files to check the name.")

        val bytes = runCatching {
            context.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return ToolResult.Failure("Could not read '$name'.")

        return context.ocr.recognizeText(bytes).fold(
            onSuccess = { text ->
                if (text.isBlank()) ToolResult.Success("No text detected in the image.")
                else ToolResult.Success("Text in $name:\n" + text.take(6_000))
            },
            onFailure = { failure -> ToolResult.Failure("OCR failed: ${failure.message}") },
        )
    }
}

class GenerateQrTool @Inject constructor() : DeviceTool {

    override val spec = ToolSpec(
        name = "generate_qr",
        description = "Generate a QR code image from text or a link; returns the saved PNG path.",
        parametersJson = """{"type":"object","properties":{"text":{"type":"string"},"size":{"type":"integer","description":"pixels, 256-1024"}},"required":["text"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val text = (arguments["text"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'text' argument")
        if (text.isEmpty()) return ToolResult.Failure("Cannot encode empty text.")
        val size = ((arguments["size"] as? JsonPrimitive)?.intOrNull ?: 512).coerceIn(256, 1024)
        return try {
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            val directory = File(context.appContext.filesDir, "qr").apply { mkdirs() }
            val file = File(directory, "qr_${System.currentTimeMillis()}.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            ToolResult.Success("QR code saved: ${file.absolutePath}")
        } catch (e: Exception) {
            ToolResult.Failure("QR generation failed: ${e.message}")
        }
    }
}

class DecodeQrTool @Inject constructor() : DeviceTool {

    override val spec = ToolSpec(
        name = "decode_qr",
        description = "Decode a QR code from an image file in Downloads/Documents and return its content.",
        parametersJson = """{"type":"object","properties":{"file_name":{"type":"string"}},"required":["file_name"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val name = (arguments["file_name"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'file_name' argument")
        val collection = MediaStore.Files.getContentUri("external")
        val uri = context.appContext.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                android.net.Uri.withAppendedPath(collection, cursor.getLong(0).toString())
            } else {
                null
            }
        } ?: return ToolResult.Failure("Image '$name' not found.")
        val bitmap = runCatching {
            context.appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull() ?: return ToolResult.Failure("Could not decode '$name'.")

        val result = runCatching {
            val intArray = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val rgb = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
            val binary = com.google.zxing.common.HybridBinarizer(rgb)
            com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(binary)).text
        }.getOrNull()
        bitmap.recycle()
        return if (result.isNullOrBlank()) {
            ToolResult.Failure("No QR code found in '$name'.")
        } else {
            ToolResult.Success("QR content: $result")
        }
    }
}
