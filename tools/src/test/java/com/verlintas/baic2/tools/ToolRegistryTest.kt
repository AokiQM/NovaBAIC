package com.verlintas.baic2.tools

import com.verlintas.baic2.core.model.AppMode
import com.verlintas.baic2.core.model.DangerLevel
import com.verlintas.baic2.core.model.ToolResult
import com.verlintas.baic2.core.model.ToolSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject

class ToolRegistryTest {

    private fun fakeTool(name: String, readOnly: Boolean) = object : DeviceTool {
        override val spec = ToolSpec(
            name = name,
            description = name,
            readOnly = readOnly,
            danger = DangerLevel.LOW,
        )

        override suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult =
            ToolResult.Success("ok")
    }

    private val registry = ToolRegistry(
        setOf(
            fakeTool("get_time", readOnly = true),
            fakeTool("open_app", readOnly = false),
            fakeTool("compute", readOnly = true),
        ),
    )

    @Test
    fun chatModeExposesNoTools() {
        assertTrue(registry.specs(AppMode.CHAT).isEmpty())
    }

    @Test
    fun chatPlusExposesOnlyReadOnlyTools() {
        val names = registry.specs(AppMode.CHAT_PLUS).map { it.name }.sorted()
        assertEquals(listOf("compute", "get_time"), names)
    }

    @Test
    fun actAndMaxExposeEverything() {
        assertEquals(3, registry.specs(AppMode.ACT).size)
        assertEquals(3, registry.specs(AppMode.MAX).size)
    }

    @Test
    fun findResolvesByName() {
        assertEquals("open_app", registry.find("open_app")?.name)
        assertEquals(null, registry.find("nope"))
    }
}
