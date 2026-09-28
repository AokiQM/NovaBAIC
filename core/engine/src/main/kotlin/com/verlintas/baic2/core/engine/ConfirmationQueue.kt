package com.verlintas.baic2.core.engine

import com.verlintas.baic2.core.model.ToolCall
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeout

/**
 * Bridge between the suspended agent loop and the confirmation UI: the loop
 * awaits [confirm] while the UI answers through [respond]. Unanswered requests
 * time out to "deny" so a run can never hang forever.
 */
class ConfirmationQueue(
    private val timeoutMs: Long = 300_000,
) {

    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private val _requests = MutableSharedFlow<ToolCall>(extraBufferCapacity = 8)
    val requests: SharedFlow<ToolCall> = _requests

    suspend fun confirm(call: ToolCall): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        if (pending.putIfAbsent(call.id, deferred) != null) {
            return false
        }
        if (!_requests.tryEmit(call)) {
            pending.remove(call.id)
            return false
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            false
        } finally {
            pending.remove(call.id)
        }
    }

    fun respond(callId: String, allow: Boolean): Boolean =
        pending.remove(callId)?.complete(allow) ?: false
}
