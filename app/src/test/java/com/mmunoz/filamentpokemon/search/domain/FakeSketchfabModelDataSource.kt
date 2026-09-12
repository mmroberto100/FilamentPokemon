package com.mmunoz.filamentpokemon.search.domain

import com.mmunoz.filamentpokemon.core.domain.model.GlbArchive
import com.mmunoz.filamentpokemon.core.domain.model.PokemonModel
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result

/** In-memory fake: serves a queue of page results and records every call. */
class FakeSketchfabModelDataSource : SketchfabModelDataSource {

    data class SearchCall(val query: String, val maxFaceCount: Int, val cursor: String?)

    val searchCalls = mutableListOf<SearchCall>()
    val pageQueue = ArrayDeque<Result<SearchPage, DataError.Network>>()
    var modelResult: Result<PokemonModel, DataError.Network> = Result.Error(DataError.Network.NOT_FOUND)

    override suspend fun search(query: String, maxFaceCount: Int, cursor: String?): Result<SearchPage, DataError.Network> {
        searchCalls += SearchCall(query, maxFaceCount, cursor)
        return pageQueue.removeFirstOrNull() ?: Result.Success(SearchPage(emptyList(), null))
    }

    override suspend fun getModel(uid: String): Result<PokemonModel, DataError.Network> = modelResult

    companion object {
        fun model(uid: String, faceCount: Int = 10_000, name: String = "Model $uid") = PokemonModel(
            uid = uid, name = name, faceCount = faceCount, vertexCount = faceCount / 2,
            glbArchive = GlbArchive(sizeBytes = 1_000, faceCount = faceCount, vertexCount = 1, textureCount = 1, textureMaxResolution = 512),
            thumbnailUrl = "https://example.com/$uid.jpg", author = "author", licenseLabel = "CC Attribution",
            viewerUrl = "https://sketchfab.com/3d-models/$uid", isAnimated = false, isDownloadable = true
        )
    }
}
