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
    retryAfterSeconds = parseRetryAfter(retryAfterHeader)?.coerceIn(0, 3_600)?.toInt(),
)

internal fun mapIOException(e: IOException): ProviderError =
    ProviderError(kind = ioErrorKind(e), message = e.message ?: e.javaClass.simpleName)

/** Retry-After may be a delay in seconds or an HTTP date; both are accepted. */
internal fun parseRetryAfter(header: String?): Long? {
    if (header.isNullOrBlank()) return null
    val trimmed = header.trim()
    trimmed.toLongOrNull()?.let { return it }
    return runCatching {
        val target = java.time.ZonedDateTime.parse(
            trimmed,
            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME,
        )
        java.time.Duration.between(java.time.ZonedDateTime.now(), target).seconds
    }.getOrNull()
}

internal fun retryDelaySeconds(header: String?): Long =
    parseRetryAfter(header)?.coerceIn(0, 10) ?: 1

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
