package com.verlintas.baic2.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelContextWindowsTest {

    @Test
    fun knownFamiliesResolve() {
        assertEquals(200_000L, ModelContextWindows.forModel("claude-sonnet-4-5"))
        assertEquals(1_000_000L, ModelContextWindows.forModel("gemini-2.5-flash"))
        assertEquals(64_000L, ModelContextWindows.forModel("deepseek-chat"))
        assertEquals(128_000L, ModelContextWindows.forModel("gpt-4o-mini"))
        assertEquals(128_000L, ModelContextWindows.forModel("qwen-plus"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(200_000L, ModelContextWindows.forModel("Claude-Opus"))
    }

    @Test
    fun unknownModelReturnsNull() {
        assertNull(ModelContextWindows.forModel("my-local-model"))
    }
}
