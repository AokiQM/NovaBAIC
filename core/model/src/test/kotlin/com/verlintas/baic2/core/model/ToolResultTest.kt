package com.verlintas.baic2.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolResultTest {

    private val json = Json

    @Test
    fun failureDefaultsToRecoverable() {
        val result = ToolResult.Failure("missing contacts permission")
        assertTrue(result.recoverable)
    }

    @Test
    fun serializationRoundTripPreservesSealedVariant() {
        val original: ToolResult = ToolResult.Failure("boom", recoverable = false)

        val decoded = json.decodeFromString<ToolResult>(json.encodeToString(original))

        assertEquals(original, decoded)
    }

    @Test
    fun deniedRoundTrip() {
        val original: ToolResult = ToolResult.Denied("Plan mode forbids write tools")

        val decoded = json.decodeFromString<ToolResult>(json.encodeToString(original))

        assertEquals(original, decoded)
    }
}
