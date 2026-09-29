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

/**
 * On Android 11+ reading files this app does not own requires "All files
 * access"; below that it is the runtime READ_EXTERNAL_STORAGE grant.
 */
internal fun hasPublicFilesAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

private const val FILES_ACCESS_HINT =
    "Files access is not granted, so public files are invisible. " +
        "Open Settings → Permissions → Files access, enable it, then retry."

internal fun filesAccessFailure(): ToolResult.Failure = ToolResult.Failure(FILES_ACCESS_HINT)

private fun ToolContext.findPublicFile(scope: String, name: String): Uri? {
    val collection = MediaStore.Files.getContentUri("external")
    val selection = StringBuilder("${MediaStore.MediaColumns.DISPLAY_NAME} = ?")
    val args = mutableListOf(name)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection.append(
            " AND (${MediaStore.MediaColumns.RELATIVE_PATH} = ?" +
                " OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)",
        )
        args += "$scope/"
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

/**
 * Read side of the file toolbox: one tool for listing (with filters), reading
 * (paged with offset/limit) and inspecting public files.
 */
class FileReadTool : DeviceTool {

    override val spec = ToolSpec(
        name = "files",
        description = "Work with files in Downloads/Documents: action=list (optional 'query' filter, " +
            "scope=downloads|documents|all), action=read ('name', optional offset/limit characters), " +
            "action=info ('name'). Reads are paged and report total size.",
        parametersJson = """{"type":"object","properties":{"action":{"type":"string","enum":["list","read","info"]},"name":{"type":"string","description":"file name for read/info"},"query":{"type":"string","description":"name filter for list"},"scope":{"type":"string","enum":["downloads","documents","all"]},"offset":{"type":"integer","description":"character offset for read"},"limit":{"type":"integer","description":"characters to read, default 8000"}},"required":["action"]}""",
        readOnly = true,
        danger = DangerLevel.LOW,
        parallelSafe = true,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = (arguments["action"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: return ToolResult.Failure("Missing 'action' argument")
        return when (action) {
            "list" -> list(context, arguments)
            "read" -> read(context, arguments)
            "info" -> info(context, arguments)
            else -> ToolResult.Failure("Unknown action '$action'. Use list|read|info.")
        }
    }

    private fun scopesFor(scope: String): List<String> = when (scope) {
        "documents" -> listOf(DOCUMENTS)
        "all" -> listOf(DOWNLOADS, DOCUMENTS)
        else -> listOf(DOWNLOADS)
    }

    private fun list(context: ToolContext, arguments: JsonObject): ToolResult {
        val scope = (arguments["scope"] as? JsonPrimitive)?.content?.lowercase() ?: "downloads"
        val query = (arguments["query"] as? JsonPrimitive)?.content?.lowercase()?.takeIf { it.isNotBlank() }
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 50).coerceIn(1, 100)
        val rows = scopesFor(scope).flatMap { folder -> queryPublicFiles(context, folder, query, limit) }
        if (rows.isEmpty()) {
            val where = if (query == null) "" else " matching '$query'"
            val hint = if (hasPublicFilesAccess(context.appContext)) "" else " $FILES_ACCESS_HINT"
            return ToolResult.Success("No files$where in ${scopesFor(scope).joinToString("/")}.$hint")
        }
        return ToolResult.Success(
            "Files:\n" + rows.take(limit).joinToString("\n") { (name, size) -> "- $name (${size / 1024} KB)" },
        )
    }

    private suspend fun read(context: ToolContext, arguments: JsonObject): ToolResult {
        val name = (arguments["name"] as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name' (file to read)")
        val scope = (arguments["scope"] as? JsonPrimitive)?.content?.lowercase() ?: "downloads"
        val offset = ((arguments["offset"] as? JsonPrimitive)?.intOrNull ?: 0).coerceAtLeast(0)
        val limit = ((arguments["limit"] as? JsonPrimitive)?.intOrNull ?: 8_000).coerceIn(200, 60_000)

        val located = locate(context, name, scope)
            ?: return if (hasPublicFilesAccess(context.appContext)) {
                ToolResult.Failure("File '$name' not found. Use files action=list to see what is there.")
            } else {
                filesAccessFailure()
            }
        val bytes = runCatching {
            context.appContext.contentResolver.openInputStream(located.first)?.use { it.readBytes() }
        }.getOrNull() ?: return ToolResult.Failure("Could not read '$name'.")
        val text = String(bytes, Charsets.UTF_8)
        if (offset >= text.length) {
            return ToolResult.Success("'${located.second}' has ${text.length} characters; offset $offset is past the end.")
        }
        val chunk = text.substring(offset, minOf(text.length, offset + limit))
        val more = offset + chunk.length < text.length
        return ToolResult.Success(
            buildString {
                append("'").append(located.second).append("' (chars ").append(offset).append('-')
                    .append(offset + chunk.length).append(" of ").append(text.length).append("):\n")
                append(chunk)
                if (more) {
                    append("\n…(more: call files action=read name=").append(located.second)
                        .append(" offset=").append(offset + chunk.length).append(')')
                }
            },
        )
    }

    private fun info(context: ToolContext, arguments: JsonObject): ToolResult {
        val name = (arguments["name"] as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name'")
        val scope = (arguments["scope"] as? JsonPrimitive)?.content?.lowercase() ?: "downloads"
        val located = locate(context, name, scope)
            ?: return if (hasPublicFilesAccess(context.appContext)) {
                ToolResult.Failure("File '$name' not found.")
            } else {
                filesAccessFailure()
            }
        val details = queryPublicFileDetails(context, located.first)
        return ToolResult.Success(
            "'${located.second}'" + (details?.let { "\n$it" } ?: "") + "\nuri: ${located.first}",
        )
    }

    private fun locate(context: ToolContext, name: String, scope: String): Pair<Uri, String>? {
        scopesFor(scope).forEach { folder ->
            context.findPublicFile(folder, name)?.let { uri -> return uri to name }
        }
        // Smart fallback: search by base name, then prefer same-extension and
        // shortest names (e.g. "report.md" resolves "report (1).md").
        val base = name.substringBeforeLast('.', name).lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        val candidates = scopesFor(scope)
            .flatMap { folder -> queryPublicFiles(context, folder, base, 20).map { it.first } }
            .distinct()
            .sortedWith(
                compareBy(
                    { candidate ->
                        if (extension.isNotEmpty() && candidate.lowercase().endsWith(".$extension")) 0 else 1
                    },
                    { it.length },
                ),
            )
        val resolved = candidates.firstOrNull() ?: return null
        scopesFor(scope).forEach { folder ->
            context.findPublicFile(folder, resolved)?.let { uri -> return uri to resolved }
        }
        return null
    }

    private fun queryPublicFiles(
        context: ToolContext,
        folder: String,
        filter: String?,
        limit: Int,
    ): List<Pair<String, Long>> {
        val collection = MediaStore.Files.getContentUri("external")
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "(${MediaStore.MediaColumns.RELATIVE_PATH} = ?" +
                " OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)"
        } else {
            "${MediaStore.MediaColumns.DATA} LIKE ?"
        }
        return runCatching {
            context.appContext.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                ),
                selection,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf("$folder/", "$folder/%")
                } else {
                    arrayOf("%/$folder/%")
                },
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val name = cursor.getString(0) ?: continue
                        if (filter != null && !name.lowercase().contains(filter)) continue
                        add(name to cursor.getLong(1))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun queryPublicFileDetails(context: ToolContext, uri: Uri): String? = runCatching {
        context.appContext.contentResolver.query(
            uri,
            arrayOf(
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val size = cursor.getLong(0)
            val mime = cursor.getString(1) ?: "unknown"
            val modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT)
                .format(java.util.Date(cursor.getLong(2) * 1000))
            "size: ${size / 1024} KB\nmime: $mime\nmodified: $modified"
        }
    }.getOrNull()
}

/**
 * Write side of the file toolbox (separate so read-only Chat+ mode can still
 * browse files): write, append and delete in Downloads.
 */
class FileWriteTool : DeviceTool {

    override val spec = ToolSpec(
        name = "file_write",
        description = "Create/overwrite (action=write), append to (action=append) or delete " +
            "(action=delete) a text file in Downloads. Markdown/txt/html/csv/json supported.",
        parametersJson = """{"type":"object","properties":{"action":{"type":"string","enum":["write","append","delete"]},"name":{"type":"string","description":"file name"},"content":{"type":"string","description":"text for write/append"},"mime_type":{"type":"string"}},"required":["action","name"]}""",
        readOnly = false,
        danger = DangerLevel.MEDIUM,
        parallelSafe = false,
    )

    override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult {
        val action = (arguments["action"] as? JsonPrimitive)?.content?.trim()?.lowercase()
            ?: return ToolResult.Failure("Missing 'action' argument")
        val name = (arguments["name"] as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Failure("Missing 'name' argument")
        val content = (arguments["content"] as? JsonPrimitive)?.content
        return when (action) {
            "write" -> {
                if (content == null) return ToolResult.Failure("Missing 'content' for write")
                write(context, name, content.toByteArray(), arguments)
            }

            "append" -> {
                if (content == null) return ToolResult.Failure("Missing 'content' for append")
                val existing = context.findPublicFile(DOWNLOADS, name)?.let { uri ->
                    runCatching {
                        context.appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                } ?: ByteArray(0)
                write(context, name, existing + content.toByteArray(), arguments)
            }

            "delete" -> {
                val uri = context.findPublicFile(DOWNLOADS, name)
                    ?: return if (hasPublicFilesAccess(context.appContext)) {
                        ToolResult.Failure("File '$name' not found in Downloads.")
                    } else {
                        filesAccessFailure()
                    }
                val deleted = runCatching {
                    context.appContext.contentResolver.delete(uri, null, null)
                }.getOrDefault(0)
                if (deleted > 0) {
                    ToolResult.Success("Deleted $name from Downloads.")
                } else {
                    ToolResult.Failure("MediaStore refused to delete '$name'.")
                }
            }

            else -> ToolResult.Failure("Unknown action '$action'. Use write|append|delete.")
        }
    }

    private fun write(
        context: ToolContext,
        name: String,
        bytes: ByteArray,
        arguments: JsonObject,
    ): ToolResult {
        val mime = (arguments["mime_type"] as? JsonPrimitive)?.content?.trim()
            ?.takeIf { it.isNotBlank() } ?: guessMime(name)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.appContext.contentResolver
                val existing = if (hasPublicFilesAccess(context.appContext)) {
                    context.findPublicFile(DOWNLOADS, name)
                } else {
                    null
                }
                val uri = if (existing != null) {
                    resolver.openOutputStream(existing, "wt")?.use { it.write(bytes) }
                    existing
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    val inserted = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: throw IOException("MediaStore refused the insert")
                    resolver.openOutputStream(inserted)?.use { it.write(bytes) }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(inserted, values, null, null)
                    inserted
                }
                ToolResult.Success("Saved $name (${bytes.size / 1024} KB) to Downloads ($uri)")
            } else {
                val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(directory, name).apply { writeBytes(bytes) }
                ToolResult.Success("Saved $name (${bytes.size / 1024} KB) to Downloads.")
            }
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
