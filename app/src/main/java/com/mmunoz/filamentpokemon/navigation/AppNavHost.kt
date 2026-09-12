package com.mmunoz.filamentpokemon.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mmunoz.filamentpokemon.search.presentation.navigation.SearchRoute
import com.mmunoz.filamentpokemon.search.presentation.navigation.searchGraph
import com.mmunoz.filamentpokemon.viewer.presentation.navigation.ViewerRoute
import com.mmunoz.filamentpokemon.viewer.presentation.navigation.viewerGraph

/** Assembles the feature graphs; cross-feature navigation is expressed here as callbacks. */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = SearchRoute) {
        searchGraph(
            onNavigateToViewer = { uid, name -> navController.navigate(ViewerRoute(uid, name)) }
        )
        viewerGraph(
            onNavigateBack = { navController.popBackStack() }
        )
    }
}
