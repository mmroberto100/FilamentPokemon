package com.mmunoz.filamentpokemon.core.data.networking

import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object HttpClientFactory {

    /**
     * @param engine   injected so tests can pass a `MockEngine`.
     * @param apiToken Sketchfab personal API token; blank means "unauthenticated" (search still works).
     * @param enableLogging wire-level logging through Kermit – debug builds only.
     */
    fun create(
        engine: HttpClientEngine,
        apiToken: String,
        enableLogging: Boolean = false
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
}
