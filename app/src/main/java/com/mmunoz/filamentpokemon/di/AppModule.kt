package com.mmunoz.filamentpokemon.di

import coil3.ImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.mmunoz.filamentpokemon.BuildConfig
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.data.networking.OkHttpEngineFactory
import com.mmunoz.filamentpokemon.core.data.preferences.DataStoreUserPreferences
import com.mmunoz.filamentpokemon.core.data.preferences.userPreferencesDataStore
import com.mmunoz.filamentpokemon.core.domain.preferences.UserPreferences
import com.mmunoz.filamentpokemon.search.data.KtorSketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.presentation.SearchViewModel
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val IMAGE_HTTP_CLIENT = named("images")

val appModule = module {
    /** Authenticated client for api.sketchfab.com. */
    single<HttpClient> {
        HttpClientFactory.create(
            engine = OkHttpEngineFactory.create(),
            apiToken = BuildConfig.SKETCHFAB_API_TOKEN,
            enableLogging = BuildConfig.DEBUG
        )
    }

    /** Unauthenticated client for thumbnails – the API token must never reach the media CDN. */
    single<HttpClient>(IMAGE_HTTP_CLIENT) {
        HttpClientFactory.create(engine = OkHttpEngineFactory.create(), apiToken = "")
    }

    single<ImageLoader> {
        ImageLoader.Builder(androidContext())
            .components { add(KtorNetworkFetcherFactory(get<HttpClient>(IMAGE_HTTP_CLIENT))) }
            .crossfade(true)
            .build()
    }

    // Preferences
    single<UserPreferences> { DataStoreUserPreferences(androidContext().userPreferencesDataStore) }

    // Search
    single<SketchfabModelDataSource> { KtorSketchfabModelDataSource(get()) }
    viewModelOf(::SearchViewModel)
}
