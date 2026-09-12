package com.mmunoz.filamentpokemon.core.data.networking

object SketchfabApi {
    const val BASE_URL = "https://api.sketchfab.com/v3"

    /** Header scheme for a personal API token (Step 10 swaps this for OAuth `Bearer`). */
    const val TOKEN_SCHEME = "Token"
}

/** Resolves a relative route against [SketchfabApi.BASE_URL]; absolute URLs pass through untouched. */
fun constructRoute(route: String): String {
    return when {
        route.startsWith("http://") || route.startsWith("https://") -> route
        route.startsWith("/") -> SketchfabApi.BASE_URL + route
        else -> SketchfabApi.BASE_URL + "/$route"
    }
}
