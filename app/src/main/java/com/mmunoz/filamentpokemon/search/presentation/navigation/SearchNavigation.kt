package com.mmunoz.filamentpokemon.search.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mmunoz.filamentpokemon.search.presentation.SearchRoot
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavGraphBuilder.searchGraph(
    onNavigateToViewer: (uid: String, name: String) -> Unit
) {
    composable<SearchRoute> {
        SearchRoot(onNavigateToViewer = onNavigateToViewer)
    }
}
