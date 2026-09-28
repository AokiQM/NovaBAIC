package com.verlintas.baic2.core.engine

import com.verlintas.baic2.core.model.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ConfirmationQueueTest {

    private val call = ToolCall(id = "c1", name = "open_app", argumentsJson = "{}")

    @Test
    fun confirmWaitsForTheUserResponse() = runTest {
        val queue = ConfirmationQueue(timeoutMs = 10_000)
        val result = async { queue.confirm(call) }
        val request = queue.requests.first()
        assertEquals("c1", request.id)
        assertTrue(queue.respond("c1", allow = true))
        assertTrue(result.await())
    }

    @Test
    fun denialIsReported() = runTest {
        val queue = ConfirmationQueue(timeoutMs = 10_000)
        val result = async { queue.confirm(call) }
        queue.requests.first()
        queue.respond("c1", allow = false)
        assertFalse(result.await())
    }

    @Test
    fun timeoutDenies() = runTest {
        val queue = ConfirmationQueue(timeoutMs = 50)
        assertFalse(queue.confirm(call))
    }

    @Test
    fun unknownResponseIsIgnored() = runTest {
        val queue = ConfirmationQueue(timeoutMs = 10_000)
        assertFalse(queue.respond("nope", allow = true))
    }
}
