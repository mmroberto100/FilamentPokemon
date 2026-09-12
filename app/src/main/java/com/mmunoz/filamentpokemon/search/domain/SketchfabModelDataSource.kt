package com.mmunoz.filamentpokemon.search.domain

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result

interface SketchfabModelDataSource {

    /**
     * Searches downloadable Pokémon models. [query] is appended to the mandatory
     * `pokemon` term. Only models with `faceCount <= maxFaceCount` (server filter
     * + client re-check) and a `.glb` archive are returned.
     */
    suspend fun search(
        query: String,
        maxFaceCount: Int,
        cursor: String? = null
    ): Result<SearchPage, DataError.Network>

    /** Fresh metadata for one model (no archive info — see [PokemonModel.glbArchive]). */
    suspend fun getModel(uid: String): Result<PokemonModel, DataError.Network>
}
