package com.mmunoz.filamentpokemon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mmunoz.filamentpokemon.search.presentation.SearchRoot
import com.mmunoz.filamentpokemon.ui.theme.FilamentPokemonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilamentPokemonTheme {
                // Step 5 replaces this with the NavHost; the viewer route lands there.
                SearchRoot(onNavigateToViewer = { _, _ -> })
            }
        }
    }
}
