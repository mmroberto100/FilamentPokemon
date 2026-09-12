package com.mmunoz.filamentpokemon.di

import com.mmunoz.filamentpokemon.BuildConfig
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.search.data.KtorSketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
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

    single<SketchfabModelDataSource> { KtorSketchfabModelDataSource(get()) }
}
