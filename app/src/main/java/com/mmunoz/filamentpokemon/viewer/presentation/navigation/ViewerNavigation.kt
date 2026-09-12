package com.mmunoz.filamentpokemon.viewer.presentation.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mmunoz.filamentpokemon.viewer.presentation.ViewerScreen
import kotlinx.serialization.Serializable

@Serializable
data class ViewerRoute(val uid: String, val name: String)

fun NavGraphBuilder.viewerGraph(
    onNavigateBack: () -> Unit
) {
    composable<ViewerRoute> { backStackEntry ->
        val route: ViewerRoute = backStackEntry.toRoute()
        ViewerScreen(uid = route.uid, name = route.name, onNavigateBack = onNavigateBack)
    }
}
