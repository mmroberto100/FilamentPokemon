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
import com.mmunoz.filamentpokemon.viewer.data.KtorGlbDownloader
import com.mmunoz.filamentpokemon.viewer.data.ModelCacheManager
import com.mmunoz.filamentpokemon.viewer.domain.GlbDownloader
import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import io.ktor.client.HttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val UNAUTHENTICATED_HTTP_CLIENT = named("unauthenticated")

val appModule = module {
    /** Authenticated client for api.sketchfab.com. */
    single<HttpClient> {
        HttpClientFactory.create(
            engine = OkHttpEngineFactory.create(),
            apiToken = BuildConfig.SKETCHFAB_API_TOKEN,
            enableLogging = BuildConfig.DEBUG
        )
    }

    /**
     * Unauthenticated client for thumbnails and S3 model downloads – the API token must never
     * reach the media CDN, and S3 rejects pre-signed URLs that also carry an Authorization header.
     */
    single<HttpClient>(UNAUTHENTICATED_HTTP_CLIENT) {
        HttpClientFactory.create(engine = OkHttpEngineFactory.create(), apiToken = "")
    }

    single<ImageLoader> {
        ImageLoader.Builder(androidContext())
            .components { add(KtorNetworkFetcherFactory(get<HttpClient>(UNAUTHENTICATED_HTTP_CLIENT))) }
            .crossfade(true)
            .build()
    }

    // Preferences
    single<UserPreferences> { DataStoreUserPreferences(androidContext().userPreferencesDataStore) }

    // Search
    single<SketchfabModelDataSource> { KtorSketchfabModelDataSource(get()) }
    viewModelOf(::SearchViewModel)

    // Viewer: temporary .glb storage strictly under context.cacheDir
    single<ModelCache> { ModelCacheManager(cacheDir = androidContext().cacheDir) }
    single<GlbDownloader> {
        KtorGlbDownloader(apiClient = get(), downloadClient = get(UNAUTHENTICATED_HTTP_CLIENT))
    }
}
