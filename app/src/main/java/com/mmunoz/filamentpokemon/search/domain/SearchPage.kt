package com.mmunoz.filamentpokemon.search.domain

data class SearchPage(
    val models: List<PokemonModel>,
    /** Opaque Sketchfab cursor for the next page; `null` when this is the last page. */
    val nextCursor: String?
)
