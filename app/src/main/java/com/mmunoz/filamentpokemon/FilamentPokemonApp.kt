package com.mmunoz.filamentpokemon

import android.app.Application
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import com.mmunoz.filamentpokemon.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FilamentPokemonApp : Application() {

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
}
