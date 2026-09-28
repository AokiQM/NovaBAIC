package com.verlintas.baic2.core.network.provider

import com.verlintas.baic2.core.model.ProviderError
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val BODY_LIMIT = 2_000
internal const val RETRY_DELAY_MS = 500L

internal fun httpErrorKind(code: Int): ProviderError.Kind = when {
    code == 401 || code == 403 -> ProviderError.Kind.AUTH
    code == 429 -> ProviderError.Kind.RATE_LIMIT
    code in 400..499 -> ProviderError.Kind.INVALID_REQUEST
    code >= 500 -> ProviderError.Kind.SERVER
    else -> ProviderError.Kind.UNKNOWN
}

internal fun ioErrorKind(e: IOException): ProviderError.Kind =
    if (e is SocketTimeoutException) ProviderError.Kind.TIMEOUT else ProviderError.Kind.NETWORK

internal fun mapHttpError(
    code: Int,
    retryAfterHeader: String?,
    message: String?,
): ProviderError = ProviderError(
    kind = httpErrorKind(code),
    message = message ?: "HTTP $code",
    httpStatus = code,
    retryAfterSeconds = retryAfterHeader?.toIntOrNull(),
)

internal fun mapIOException(e: IOException): ProviderError =
    ProviderError(kind = ioErrorKind(e), message = e.message ?: e.javaClass.simpleName)

internal fun retryDelaySeconds(header: String?): Long =
    header?.toLongOrNull()?.coerceIn(0, 10) ?: 1

/**
 * Runs one HTTP call, retrying once for retryable statuses (408/429/5xx) or
 * connection failures. The request is rebuilt for each attempt: OkHttp calls
 * are single-use.
 */
internal suspend fun OkHttpClient.executeWithRetry(
    requestBuilder: () -> Request,
): Pair<Call, Response> {
    var attempt = 0
    while (true) {
        try {
            val call = newCall(requestBuilder())
            val response = call.execute()
            if (response.isSuccessful || attempt >= 1 || !isRetryable(response.code)) {
                return call to response
            }
            val seconds = retryDelaySeconds(response.header("Retry-After"))
            response.close()
            delay(seconds * 1_000)
            attempt++
        } catch (e: IOException) {
            if (attempt >= 1) throw e
            delay(RETRY_DELAY_MS)
            attempt++
        }
    }
}

private fun isRetryable(code: Int): Boolean = code == 408 || code == 429 || code in 500..599
