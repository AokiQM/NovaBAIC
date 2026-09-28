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

package com.verlintas.baic2.core.network.sse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class SseParserTest {

    @Test
    fun dispatchesSingleEventOnBlankLine() {
        val parser = SseParser()
        assertNull(parser.line("data: hello"))
        val event = parser.line("")
        assertEquals("hello", event?.data)
    }

    @Test
    fun joinsMultiLineDataWithNewline() {
        val parser = SseParser()
        parser.line("data: part1")
        parser.line("data: part2")
        val event = parser.line("")
        assertEquals("part1\npart2", event?.data)
    }

    @Test
    fun parsesEventNameAndId() {
        val parser = SseParser()
        parser.line("event: message")
        parser.line("id: 42")
        parser.line("data: x")
        val event = parser.line("")
        assertEquals("message", event?.event)
        assertEquals("42", event?.id)
        assertEquals("x", event?.data)
    }

    @Test
    fun ignoresComments() {
        val parser = SseParser()
        parser.line(": keep-alive")
        assertNull(parser.line(""))
        parser.line("data: ok")
        assertEquals("ok", parser.line("")?.data)
    }

    @Test
    fun stripsOnlyOneLeadingSpace() {
        val parser = SseParser()
        parser.line("data:  two spaces")
        assertEquals(" two spaces", parser.line("")?.data)
    }

    @Test
    fun handlesCrLfLines() {
        val parser = SseParser()
        parser.line("data: crlf\r")
        assertEquals("crlf", parser.line("")?.data)
    }

    @Test
    fun flushOnEndOfStream() {
        val parser = SseParser()
        parser.line("data: last")
        assertEquals("last", parser.endOfStream()?.data)
        assertNull(parser.endOfStream())
    }

    @Test
    fun preservesJsonPayloadExactly() {
        val json = """{"choices":[{"delta":{"content":"a\nb"}}]}"""
        val parser = SseParser()
        parser.line("data: $json")
        assertEquals(json, parser.line("")?.data)
    }

    @Test
    fun rejectsOversizedEvent() {
        val parser = SseParser(maxEventBytes = 16)
        parser.line("data: 0123456789")
        assertFailsWith<IllegalStateException> {
            parser.line("data: 0123456789")
        }
    }

    @Test
    fun dataWithoutColonIsAcceptedAsEmptyValue() {
        val parser = SseParser()
        parser.line("data")
        val event = parser.line("")
        assertEquals("", event?.data)
    }
}
