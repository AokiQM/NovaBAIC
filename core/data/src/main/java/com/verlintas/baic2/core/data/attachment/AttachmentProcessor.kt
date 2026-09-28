package com.verlintas.baic2.core.data.attachment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.verlintas.baic2.core.model.Attachment
import com.verlintas.baic2.core.model.AttachmentKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports picker URIs into app-private files (images, downscaled) or inline
 * text (bounded), and materializes base64 payloads for outbound requests.
 */
@Singleton
class AttachmentProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun importImage(uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "unreadable_image" }

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > MAX_DIMENSION * 2 ||
                bounds.outHeight / sampleSize > MAX_DIMENSION * 2
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: error("unreadable_image")

            val scaled = scaleDown(decoded, MAX_DIMENSION)
            val directory = File(context.filesDir, ATTACHMENT_DIR).apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()

            Attachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.IMAGE,
                mimeType = "image/jpeg",
                fileName = displayName(uri),
                localPath = file.absolutePath,
                sizeBytes = file.length(),
            )
        }
    }

    suspend fun importTextFile(uri: Uri): Result<Attachment> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (total <= MAX_TEXT_BYTES) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    total += read
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: error("unreadable_file")
            require(bytes.size <= MAX_TEXT_BYTES) { "file_too_large" }
            val mime = context.contentResolver.getType(uri) ?: "text/plain"
            require(mime.startsWith("text/") || mime in TEXT_LIKE_MIMES) { "unsupported_type" }
            Attachment(
                id = UUID.randomUUID().toString(),
                kind = AttachmentKind.TEXT,
                mimeType = mime,
                fileName = displayName(uri) ?: "attachment.txt",
                text = String(bytes, Charsets.UTF_8),
                sizeBytes = bytes.size.toLong(),
            )
        }
    }

    /** Fills [Attachment.base64] for images; other kinds pass through. */
    suspend fun withBase64(attachment: Attachment): Attachment = withContext(Dispatchers.IO) {
        if (attachment.kind != AttachmentKind.IMAGE || attachment.base64 != null) return@withContext attachment
        val path = attachment.localPath ?: return@withContext attachment
        val file = File(path)
        if (!file.exists()) return@withContext attachment
        val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        attachment.copy(base64 = encoded)
    }

    suspend fun delete(attachment: Attachment) = withContext(Dispatchers.IO) {
        attachment.localPath?.let { runCatching { File(it).delete() } }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
    }.getOrNull()

    private companion object {
        const val ATTACHMENT_DIR = "attachments"
        const val MAX_DIMENSION = 1600
        const val JPEG_QUALITY = 82
        const val MAX_TEXT_BYTES = 1024 * 1024
        val TEXT_LIKE_MIMES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-yaml",
        )
    }
}
