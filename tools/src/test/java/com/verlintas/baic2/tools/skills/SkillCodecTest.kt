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

import com.verlintas.baic2.core.model.Skill
import com.verlintas.baic2.core.model.SkillStep
import com.verlintas.baic2.core.model.SkillToolDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillCodecTest {

    private val yaml = """
        id: silence-phone
        name: 静音手机
        version: 2
        description: 一键静音并开启勿扰
        instructions: |
          先说一句“开始静音”，再执行。
        permissions:
          - system_settings
        tools:
          - id: silence_phone
            description: 静音并开启免打扰
            steps:
              - tool: set_ringer
                args:
                  mode: silent
              - tool: set_volume
                args:
                  level: 0
    """.trimIndent()

    @Test
    fun parsesManifest() {
        val skill = SkillCodec.parse(yaml).getOrThrow()

        assertEquals("silence-phone", skill.id)
        assertEquals("静音手机", skill.name)
        assertEquals(2, skill.version)
        assertTrue(skill.instructions.contains("开始静音"))
        assertEquals(listOf("system_settings"), skill.permissions)
        assertEquals(1, skill.tools.size)
        assertEquals("silence_phone", skill.tools[0].id)
        assertEquals(2, skill.tools[0].steps.size)
        assertEquals("set_ringer", skill.tools[0].steps[0].tool)
        assertTrue(skill.tools[0].steps[0].argsJson.contains("\"mode\":\"silent\""))
        assertTrue(skill.tools[0].steps[1].argsJson.contains("\"level\":0"))
    }

    @Test
    fun roundTripsThroughEncode() {
        val skill = Skill(
            id = "morning",
            name = "Morning",
            description = "demo",
            instructions = "do things",
            tools = listOf(
                SkillToolDef(
                    id = "morning_run",
                    description = "run",
                    steps = listOf(SkillStep("set_brightness", """{"level":80}""")),
                ),
            ),
        )

        val encoded = SkillCodec.encode(skill)
        val decoded = SkillCodec.parse(encoded).getOrThrow()

        assertEquals(skill, decoded)
    }

    @Test
    fun rejectsGarbage() {
        assertTrue(SkillCodec.parse("not: [a, mapping").isFailure)
        assertTrue(SkillCodec.parse("name: no id").isFailure)
    }

    @Test
    fun rejectsToolsWithoutSteps() {
        val result = SkillCodec.parse(
            """
            id: x
            name: X
            tools:
              - id: t
                description: d
            """.trimIndent(),
        )
        assertEquals(emptyList(), result.getOrThrow().tools)
    }
}
