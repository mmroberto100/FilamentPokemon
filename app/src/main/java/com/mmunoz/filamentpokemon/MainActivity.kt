package com.mmunoz.filamentpokemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mmunoz.filamentpokemon.navigation.AppNavHost
import com.mmunoz.filamentpokemon.ui.theme.FilamentPokemonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilamentPokemonTheme {
                AppNavHost()
            }
        }
    }
}
