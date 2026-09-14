package com.mmunoz.filamentpokemon.core.data.networking

import kotlin.random.Random

/**
 * Backoff for the answers worth a second attempt: HTTP 429 and 5xx. Other 4xx are final, and
 * exceptions are never retried – a dropped socket or a timeout can surface mid-stream, when part
 * of a download is already on disk, and re-issuing the request would not help.
 *
 * Retry `n` (1-based) waits `baseDelayMs * 2^(n-1)`, capped at [maxDelayMs], plus up to [jitterMs]
 * of random spread. A `Retry-After` header (seconds) raises the wait to what the server asked for;
 * when that alone exceeds [maxDelayMs] the answer is not retried, so the caller fails fast instead
 * of holding a screen for minutes.
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelayMs: Long = 500,
    val maxDelayMs: Long = 8_000,
    val jitterMs: Long = 250
) {

    fun shouldRetry(status: Int, retryAfterSeconds: Long?): Boolean =
        (status == 429 || status in 500..599) && retryAfterMillis(retryAfterSeconds) <= maxDelayMs

    fun delayMillis(attempt: Int, retryAfterSeconds: Long?, random: Random = Random): Long {
        val exponential = (baseDelayMs shl (attempt - 1).coerceIn(0, MAX_SHIFT)).coerceAtMost(maxDelayMs)
        val jitter = if (jitterMs > 0) random.nextLong(jitterMs + 1) else 0L
        return maxOf(exponential + jitter, retryAfterMillis(retryAfterSeconds))
    }

    private fun retryAfterMillis(seconds: Long?): Long = (seconds ?: 0L).coerceAtLeast(0) * 1000

    private companion object {
        const val MAX_SHIFT = 20
    }
}
