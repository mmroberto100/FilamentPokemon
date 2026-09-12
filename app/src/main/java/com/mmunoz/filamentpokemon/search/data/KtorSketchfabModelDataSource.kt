package com.mmunoz.filamentpokemon.search.data

import com.mmunoz.filamentpokemon.core.data.networking.get
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.core.domain.util.map
import com.mmunoz.filamentpokemon.search.data.dto.ModelDto
import com.mmunoz.filamentpokemon.search.data.dto.SearchResponseDto
import com.mmunoz.filamentpokemon.search.data.mappers.toPokemonModel
import com.mmunoz.filamentpokemon.search.data.mappers.toSearchPage
import com.mmunoz.filamentpokemon.search.domain.PokemonModel
import com.mmunoz.filamentpokemon.search.domain.SearchPage
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.domain.fitsBudget
import io.ktor.client.HttpClient

class KtorSketchfabModelDataSource(
    private val httpClient: HttpClient
) : SketchfabModelDataSource {

    override suspend fun search(
        query: String,
        maxFaceCount: Int,
        cursor: String?
    ): Result<SearchPage, DataError.Network> {
        return httpClient.get<SearchResponseDto>(
            route = "search",
            queryParameters = mapOf(
                "type" to "models",
                "q" to buildQuery(query),
                "downloadable" to true,
                // Server-side half of the polygon gate.
                "max_face_count" to maxFaceCount,
                "sort_by" to SORT_BY,
                "count" to PAGE_SIZE,
                "cursor" to cursor
            )
        ).map { response ->
            val models = response.results
                .filterNot { it.isAgeRestricted }
                .map(ModelDto::toPokemonModel)
                // Client-side half: never trust a single filter for the mobile budget.
                .filter { it.isViewable(maxFaceCount) }
            response.toSearchPage(models)
        }
    }

    override suspend fun getModel(uid: String): Result<PokemonModel, DataError.Network> {
        return httpClient.get<ModelDto>(route = "models/$uid")
            .map(ModelDto::toPokemonModel)
    }

    private fun PokemonModel.isViewable(maxFaceCount: Int): Boolean =
        isDownloadable && glbArchive != null && fitsBudget(maxFaceCount)

    private fun buildQuery(userQuery: String): String =
        (POKEMON_TERM + " " + userQuery.trim()).trim()

    private companion object {
        const val POKEMON_TERM = "pokemon"
        const val SORT_BY = "-likeCount"
        const val PAGE_SIZE = 24
    }
}
