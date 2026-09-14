package com.mmunoz.filamentpokemon.viewer.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mmunoz.filamentpokemon.viewer.presentation.ViewerRoot
import kotlinx.serialization.Serializable

@Serializable
data class ViewerRoute(val uid: String, val name: String)

fun NavGraphBuilder.viewerGraph(
    onNavigateBack: () -> Unit
) {
    // Route arguments reach ViewerViewModel through its SavedStateHandle ("uid", "name").
    composable<ViewerRoute> {
        ViewerRoot(onNavigateBack = onNavigateBack)
    }
}
