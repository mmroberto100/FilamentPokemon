package com.mmunoz.filamentpokemon.testutil

import kotlinx.serialization.json.Json

object Fixtures {
    /** Mirrors the production client configuration in HttpClientFactory. */
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun read(path: String): String =
        checkNotNull(Fixtures::class.java.classLoader?.getResource(path)) { "Missing test fixture: $path" }
            .readText()
}
