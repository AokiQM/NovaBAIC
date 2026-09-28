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

package com.verlintas.baic2.tools.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import com.verlintas.baic2.tools.DeviceTool
import com.verlintas.baic2.tools.ToolContext
import com.verlintas.baic2.tools.web.WebFetcher
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val DOWNLOADS = "Download"
private const val DOCUMENTS = "Documents"

private fun ToolContext.findPublicFile(scope: String, name: String): Uri? {
    val collection = MediaStore.Files.getContentUri("external")
    val selection = StringBuilder("${MediaStore.MediaColumns.DISPLAY_NAME} = ?")
    val args = mutableListOf(name)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection.append(" AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?")
        args += "$scope/%"
    } else {
        selection.append(" AND ${MediaStore.MediaColumns.DATA} LIKE ?")
        args += "%/$scope/%"
    }
    return appContext.contentResolver.query(
        collection,
        arrayOf(MediaStore.MediaColumns._ID),
        selection.toString(),
        args.toTypedArray(),
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            Uri.withAppendedPath(collection, cursor.getLong(0).toString())
        } else {
            null
        }
    }
}

class DownloadFileTool @Inject constructor(
    private val fetcher: WebFetcher,
) : DeviceTool {

    override val spec = ToolSpec(
        name = "download_file",
        description = "Download a file from a URL into the public Downloads folder.",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"},"file_name":{"type":"string"}},"required":["url"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val url = (arguments["url"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'url' argument")
        val name = (arguments["file_name"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: url.substringAfterLast('/').substringBefore('?').ifBlank { "download.bin" }
        val bytes = fetcher.fetchBytes(url, maxBytes = MAX_BYTES).getOrElse { failure ->
            return ToolResult.Failure("Download failed: ${failure.message}")
        }
        return try {
            val uri = saveToDownloads(context.appContext, name, bytes)
            ToolResult.Success("Saved $name to Downloads ($uri)")
        } catch (e: Exception) {
            ToolResult.Failure("Could not save the file: ${e.message}")
        }
    }

    private fun saveToDownloads(context: Context, name: String, bytes: ByteArray): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore refused the insert")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        }
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "",
        ).apply { mkdirs() }
        val file = File(directory, name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private companion object {
        const val MAX_BYTES = 20_000_000L
    }
}

class WriteDocumentTool : DeviceTool {
    override val spec = ToolSpec(
        name = "write_document",
        description = "Save text content (markdown/txt/html/csv) as a file in Downloads.",
        parametersJson = """{"type":"object","properties":{"content":{"type":"string"},"file_name":{"type":"string"},"mime_type":{"type":"string"}},"required":["content","file_name"]}""",
        readOnly = false,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val content = (arguments["content"] as? JsonPrimitive)?.content
            ?: return ToolResult.Failure("Missing 'content' argument")
        val name = (arguments["file_name"] as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() } ?: return ToolResult.Failure("Missing 'file_name' argument")
        val mime = (arguments["mime_type"] as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() } ?: guessMime(name)
        return try {
            val bytes = content.toByteArray()
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val resolver = context.appContext.contentResolver
                val inserted = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("MediaStore refused the insert")
                resolver.openOutputStream(inserted)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(inserted, values, null, null)
                inserted
            } else {
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(directory, name).apply { writeBytes(bytes) }.let { Uri.fromFile(it) }
            }
            ToolResult.Success("Saved $name (${bytes.size / 1024} KB) to Downloads ($uri)")
        } catch (e: Exception) {
            ToolResult.Failure("Could not write the document: ${e.message}")
        }
    }

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "md" -> "text/markdown"
        "html", "htm" -> "text/html"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "text/plain"
    }
}

class ListFilesTool : DeviceTool {
    override val spec = ToolSpec(
        name = "list_files",
        description = "List recent files in the public Downloads or Documents folder.",
        parametersJson = """{"type":"object","properties":{"scope":{"type":"string","enum":["downloads","documents"]},"filter":{"type":"string"},"limit":{"type":"integer"}}}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val scope = (arguments["scope"] as? JsonPrimitive)?.content?.lowercase() ?: "downloads"
        val folder = if (scope == "documents") DOCUMENTS else DOWNLOADS
        val filter = (arguments["filter"] as? JsonPrimitive)?.content?.lowercase()?.takeIf { it.isNotBlank() }
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 50).coerceIn(1, 100)

        val collection = MediaStore.Files.getContentUri("external")
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        val rows = runCatching {
            context.appContext.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_MODIFIED,
                ),
                selection,
                arrayOf("$folder/%"),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val name = cursor.getString(0) ?: continue
                        if (filter != null && !name.lowercase().contains(filter)) continue
                        add("$name (${cursor.getLong(1) / 1024} KB)")
                    }
                }
            }
        }.getOrNull().orEmpty()

        if (rows.isEmpty()) {
            return ToolResult.Success("No matching files in $folder.")
        }
        return ToolResult.Success("Files in $folder:\n" + rows.joinToString("\n") { "- $it" })
    }
}

class ReadTextFileTool @Inject constructor() : DeviceTool {
    override val spec = ToolSpec(
        name = "read_text_file",
        description = "Read a text file from Downloads/Documents by name (max 1 MB).",
        parametersJson = """{"type":"object","properties":{"file_name":{"type":"string"},"scope":{"type":"string","enum":["downloads","documents"]}},"required":["file_name"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val name = (arguments["file_name"] as? JsonPrimitive)?.content?.trim()
            ?: return ToolResult.Failure("Missing 'file_name' argument")
        val scope = (arguments["scope"] as? JsonPrimitive)?.content?.lowercase() ?: "downloads"
        val folder = if (scope == "documents") DOCUMENTS else DOWNLOADS
        val uri = context.findPublicFile(folder, name)
            ?: return ToolResult.Failure("File '$name' not found in $folder. Use list_files to see what is there.")
        val bytes = runCatching {
            context.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (total <= 1_048_576) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    total += read
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            } ?: error("unreadable")
        }.getOrElse { failure ->
            return ToolResult.Failure("Could not read '$name': ${failure.message}")
        }
        if (bytes.size > 1_048_576) return ToolResult.Failure("File is larger than 1 MB.")
        return ToolResult.Success(String(bytes, Charsets.UTF_8))
    }
}
