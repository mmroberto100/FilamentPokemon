package com.mmunoz.filamentpokemon

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import com.mmunoz.filamentpokemon.di.appModule
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

    /** Lets Coil's AsyncImage pick up the Koin-configured loader. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = get()
}
