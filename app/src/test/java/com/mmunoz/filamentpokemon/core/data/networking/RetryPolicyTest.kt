package com.mmunoz.filamentpokemon.core.data.networking

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.random.Random

class RetryPolicyTest {

    private val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 500, maxDelayMs = 8_000, jitterMs = 0)

    @Test
    fun `delay doubles from the base and stops at the cap`() {
        val delays = (1..6).map { policy.delayMillis(it, retryAfterSeconds = null) }
        assertThat(delays).isEqualTo(listOf(500L, 1_000L, 2_000L, 4_000L, 8_000L, 8_000L))
    }

    @Test
    fun `jitter adds at most jitterMs on top of the backoff`() {
        val jittered = policy.copy(jitterMs = 250)
        repeat(50) { seed ->
            assertThat(jittered.delayMillis(1, retryAfterSeconds = null, random = Random(seed))).isBetween(500L, 750L)
        }
    }

    @Test
    fun `Retry-After raises the wait to what the server asked for`() {
        assertThat(policy.delayMillis(1, retryAfterSeconds = 3)).isEqualTo(3_000L)
        assertThat(policy.delayMillis(3, retryAfterSeconds = 1)).isEqualTo(2_000L)
    }

    @ParameterizedTest(name = "HTTP {0} retried = {1}")
    @CsvSource(
        "429, true",
        "500, true",
        "502, true",
        "503, true",
        "599, true",
        "200, false",
        "400, false",
        "401, false",
        "403, false",
        "404, false",
        "408, false",
        "418, false"
    )
    fun `only 429 and 5xx are retried`(status: Int, retried: Boolean) {
        assertThat(policy.shouldRetry(status, retryAfterSeconds = null)).isEqualTo(retried)
    }

    @Test
    fun `a Retry-After beyond the cap gives up instead of waiting`() {
        assertThat(policy.shouldRetry(429, retryAfterSeconds = 8)).isTrue()
        assertThat(policy.shouldRetry(429, retryAfterSeconds = 9)).isFalse()
        assertThat(policy.shouldRetry(503, retryAfterSeconds = 120)).isFalse()
    }
}
