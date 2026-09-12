package com.mmunoz.filamentpokemon.core.domain.model

/** A Sketchfab 3D model that passed the Pokémon + downloadable filters. */
data class PokemonModel(
    val uid: String,
    val name: String,
    /** Triangle count of the whole scene as reported by Sketchfab. */
    val faceCount: Int,
    val vertexCount: Int,
    /**
     * Metadata of the `.glb` archive Sketchfab can serve. Present in search results;
     * `null` when the endpoint doesn't include archive info (e.g. `GET /models/{uid}`).
     */
    val glbArchive: GlbArchive?,
    val thumbnailUrl: String?,
    val author: String,
    val licenseLabel: String?,
    val viewerUrl: String,
    val isAnimated: Boolean,
    val isDownloadable: Boolean
)

data class GlbArchive(
    val sizeBytes: Long,
    val faceCount: Int,
    val vertexCount: Int,
    val textureCount: Int,
    val textureMaxResolution: Int
)
