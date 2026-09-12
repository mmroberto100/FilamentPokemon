package com.mmunoz.filamentpokemon.search.domain

import com.mmunoz.filamentpokemon.core.domain.model.PokemonModel
data class SearchPage(
    val models: List<PokemonModel>,
    /** Opaque Sketchfab cursor for the next page; `null` when this is the last page. */
    val nextCursor: String?
)
