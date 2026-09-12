package com.mmunoz.filamentpokemon.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mmunoz.filamentpokemon.search.presentation.navigation.SearchRoute
import com.mmunoz.filamentpokemon.search.presentation.navigation.searchGraph
import com.mmunoz.filamentpokemon.viewer.presentation.navigation.ViewerRoute
import com.mmunoz.filamentpokemon.viewer.presentation.navigation.viewerGraph

/**
 * Assembles the feature graphs; cross-feature navigation is expressed here as callbacks.
 * Each callback checks the current destination first so a double-tap during the transition
 * can neither stack a second viewer nor pop the search screen off the stack.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = SearchRoute) {
        searchGraph(
            onNavigateToViewer = { uid, name ->
                if (navController.currentDestination?.hasRoute<SearchRoute>() == true) {
                    navController.navigate(ViewerRoute(uid, name)) { launchSingleTop = true }
                }
            }
        )
        viewerGraph(
            onNavigateBack = {
                if (navController.currentDestination?.hasRoute<ViewerRoute>() == true) {
                    navController.popBackStack()
                }
            }
        )
    }
}
