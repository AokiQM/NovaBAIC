package com.verlintas.baic2.core.network.sse

/** One dispatched Server-Sent Event. */
data class SseEvent(
    val data: String,
    val event: String? = null,
    val id: String? = null,
)

/**
 * Incremental SSE parser (WHATWG-ish): fields are parsed line by line, an
 * empty line dispatches the accumulated event. Comments (`:`) are ignored,
 * multi-line `data:` values are joined with newlines, and a single leading
 * space after the colon is stripped (only one).
 *
 * The parser is deliberately pull-based so tests can feed it any chunking.
 */
class SseParser(private val maxEventBytes: Int = 1_000_000) {

    private val data = StringBuilder()
    private var hasData = false
    private var event: String? = null
    private var id: String? = null
    private var size = 0

    /** Feed one raw line (no trailing newline). Returns an event if dispatched. */
    fun line(rawLine: String): SseEvent? {
        val line = rawLine.trimEnd('\r')

        if (line.isEmpty()) return dispatch()

        if (line.startsWith(":")) return null

        val colon = line.indexOf(':')
        val field = if (colon == -1) line else line.substring(0, colon)
        val value = when {
            colon == -1 -> ""
            colon + 1 < line.length && line[colon + 1] == ' ' -> line.substring(colon + 2)
            else -> line.substring(colon + 1)
        }

        when (field) {
            "data" -> {
                size += value.length + 1
                if (size > maxEventBytes) {
                    reset()
                    throw IllegalStateException("SSE event exceeded $maxEventBytes bytes")
                }
                if (hasData) data.append('\n')
                data.append(value)
                hasData = true
            }
            "event" -> event = value
            "id" -> id = value
            "retry" -> Unit
        }
        return null
    }

    /** Flush a pending event when the stream ends without a trailing blank line. */
    fun endOfStream(): SseEvent? = if (hasData) dispatch() else null

    fun reset() {
        data.clear()
        hasData = false
        event = null
        id = null
        size = 0
    }

    private fun dispatch(): SseEvent? {
        if (!hasData) {
            reset()
            return null
        }
        val dispatched = SseEvent(data = data.toString(), event = event, id = id)
        reset()
        return dispatched
    }
}
