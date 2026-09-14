package com.mmunoz.filamentpokemon.core.data.networking

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    /**
     * @param engine   injected so tests can pass a `MockEngine`.
     * @param apiToken Sketchfab personal API token; blank means "unauthenticated" (search still works).
     * @param enableLogging wire-level logging through Kermit – debug builds only.
     * @param retryPolicy backoff for 429/5xx answers, applied before a response reaches the caller
     *                    (a streamed body is therefore never re-requested once it is being read).
     */
    fun create(
        engine: HttpClientEngine,
        apiToken: String,
        enableLogging: Boolean = false,
        retryPolicy: RetryPolicy = RetryPolicy()
    ): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    coerceInputValues = true
                }
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
        // Installed after HttpTimeout so the request timeout spans every attempt and the waits
        // between them; a retried call can never outlive the budget of a plain one.
        install(HttpRequestRetry) {
            maxRetries = retryPolicy.maxRetries
            retryIf { _, response ->
                retryPolicy.shouldRetry(response.status.value, response.retryAfterSeconds())
            }
            retryOnExceptionIf { _, _ -> false }
            delayMillis(respectRetryAfterHeader = false) { attempt ->
                retryPolicy.delayMillis(attempt, response?.retryAfterSeconds())
            }
        }
        if (enableLogging) {
            install(Logging) {
                level = LogLevel.INFO
                // Never let the API token reach logcat, whatever the level.
                sanitizeHeader { it == HttpHeaders.Authorization }
                logger = object : Logger {
                    private val log = KermitLogger.withTag("Ktor")
                    override fun log(message: String) = log.d { message }
                }
            }
        }
        defaultRequest {
            if (apiToken.isNotBlank()) {
                header(HttpHeaders.Authorization, "${SketchfabApi.TOKEN_SCHEME} $apiToken")
            }
        }
    }

    /** Only the delay-seconds form is understood; an HTTP-date value falls back to the backoff. */
    private fun HttpResponse.retryAfterSeconds(): Long? = headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()
}
