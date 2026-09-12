package com.mmunoz.filamentpokemon.search.data.mappers

import com.mmunoz.filamentpokemon.search.data.dto.ArchiveInfoDto
import com.mmunoz.filamentpokemon.search.data.dto.ImageDto
import com.mmunoz.filamentpokemon.search.data.dto.ModelDto
import com.mmunoz.filamentpokemon.search.data.dto.SearchResponseDto
import com.mmunoz.filamentpokemon.search.domain.GlbArchive
import com.mmunoz.filamentpokemon.search.domain.PokemonModel
import com.mmunoz.filamentpokemon.search.domain.SearchPage

/** Grid cards are ~half the screen wide; 720 px is the smallest Sketchfab size that stays sharp. */
private const val PREFERRED_THUMBNAIL_WIDTH = 720

fun ModelDto.toPokemonModel(): PokemonModel = PokemonModel(
    uid = uid,
    name = name,
    faceCount = faceCount,
    vertexCount = vertexCount,
    glbArchive = archives?.glb?.toGlbArchive(),
    thumbnailUrl = thumbnails?.images?.pickThumbnail(),
    author = user?.displayName?.takeIf { it.isNotBlank() } ?: user?.username.orEmpty(),
    licenseLabel = license?.label,
    viewerUrl = viewerUrl,
    isAnimated = animationCount > 0,
    isDownloadable = isDownloadable
)

fun ArchiveInfoDto.toGlbArchive(): GlbArchive = GlbArchive(
    sizeBytes = size,
    faceCount = faceCount,
    vertexCount = vertexCount,
    textureCount = textureCount,
    textureMaxResolution = textureMaxResolution
)

fun SearchResponseDto.toSearchPage(models: List<PokemonModel>): SearchPage = SearchPage(
    models = models,
    nextCursor = cursors?.next ?: next?.extractCursor()
)

/** Smallest image at least [PREFERRED_THUMBNAIL_WIDTH] wide, else the largest available. */
internal fun List<ImageDto>.pickThumbnail(): String? {
    if (isEmpty()) return null
    return (filter { it.width >= PREFERRED_THUMBNAIL_WIDTH }.minByOrNull { it.width }
        ?: maxByOrNull { it.width })?.url
}

private fun String.extractCursor(): String? =
    substringAfter("cursor=", missingDelimiterValue = "")
        .substringBefore('&')
        .takeIf { it.isNotEmpty() }
