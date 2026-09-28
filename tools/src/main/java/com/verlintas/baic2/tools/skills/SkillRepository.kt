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

package com.verlintas.baic2.tools.skills

import android.content.Context
import android.net.Uri
import com.verlintas.baic2.core.model.Skill
import com.verlintas.baic2.core.model.SkillStep
import com.verlintas.baic2.core.model.SkillToolDef
import com.verlintas.baic2.core.model.ToolCall
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill storage: one directory per skill under filesDir/skills with a
 * skill.yaml manifest. Imports are copied so the picker URI can disappear.
 */
@Singleton
class SkillRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val root: File get() = File(context.filesDir, "skills")

    suspend fun list(): List<Skill> = withContext(Dispatchers.IO) {
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        dirs.mapNotNull { dir ->
            val manifest = File(dir, "skill.yaml")
            if (!manifest.exists()) return@mapNotNull null
            SkillCodec.parse(manifest.readText()).getOrNull()
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun find(idOrName: String): Skill? = withContext(Dispatchers.IO) {
        list().firstOrNull { it.id == idOrName || it.name.equals(idOrName, ignoreCase = true) }
    }

    suspend fun import(uri: Uri, displayName: String?): Result<Skill> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (total <= MAX_MANIFEST_BYTES) {
                    val read = stream.read(chunk)
                    if (read == -1) break
                    total += read
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray().toString(Charsets.UTF_8)
            } ?: error("unreadable skill file")
            require(text.length <= MAX_MANIFEST_BYTES) { "skill_manifest_too_large" }
            val skill = SkillCodec.parse(text).getOrThrow()
            save(skill)
            skill
        }
    }

    suspend fun save(skill: Skill) = withContext(Dispatchers.IO) {
        val directory = File(root, skill.id).apply { mkdirs() }
        File(directory, "skill.yaml").writeText(SkillCodec.encode(skill))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        runCatching { File(root, id).deleteRecursively() }
    }

    /** Turns a completed run's tool calls into a replayable skill. */
    suspend fun saveFromToolCalls(name: String, calls: List<ToolCall>): Skill {
        val id = name.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "skill-${System.currentTimeMillis()}" }
        val skill = Skill(
            id = id,
            name = name,
            description = "录制自一次完成的操作序列",
            tools = listOf(
                SkillToolDef(
                    id = id.replace('-', '_'),
                    description = "Replays the recorded sequence: ${calls.joinToString(", ") { it.name }}",
                    steps = calls.map { call ->
                        SkillStep(tool = call.name, argsJson = call.argumentsJson)
                    },
                ),
            ),
        )
        save(skill)
        return skill
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 64 * 1024
    }
}
