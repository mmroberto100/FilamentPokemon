package com.mmunoz.filamentpokemon

import android.app.Application
import android.content.ComponentCallbacks2
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import com.mmunoz.filamentpokemon.di.APPLICATION_SCOPE
import com.mmunoz.filamentpokemon.di.appModule
import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FilamentPokemonApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // Load Filament native libraries once, before any Engine/AssetLoader is created.
        Filament.init()
        Gltfio.init()
        Utils.init()

        startKoin {
            androidLogger()
            androidContext(this@FilamentPokemonApp)
            modules(appModule)
        }
    }

    /**
     * CLAUDE.md §3: once the UI is hidden (or memory gets tighter than that) every model no
     * viewer is using leaves the cache dir. Leased files – an open viewer's model or a download
     * in flight – are never touched. Levels below UI_HIDDEN are the deprecated foreground hints.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        val cache = get<ModelCache>()
        get<CoroutineScope>(APPLICATION_SCOPE).launch { cache.evictUnused() }
    }

    /** Lets Coil's AsyncImage pick up the Koin-configured loader. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
