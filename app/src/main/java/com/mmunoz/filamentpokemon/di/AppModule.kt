package com.mmunoz.filamentpokemon.di

import com.mmunoz.filamentpokemon.BuildConfig
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module

/**
 * Root Koin module. Layer-specific definitions (search, viewer) are added in later steps.
 */
val appModule = module {
    single<HttpClient> {
        HttpClientFactory.create(
            engine = OkHttp.create(),
            apiToken = BuildConfig.SKETCHFAB_API_TOKEN,
            enableLogging = BuildConfig.DEBUG
        )
    }
}
