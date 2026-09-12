package com.mmunoz.filamentpokemon.search.domain

/**
 * Mobile GPU safety thresholds (see CLAUDE.md). Models above the face budget are never
 * downloaded; the user may tune the budget between [MIN_FACES] and [MAX_FACES].
 */
object PolygonBudget {
    const val DEFAULT_MAX_FACES = 30_000
    const val MIN_FACES = 5_000
    const val MAX_FACES = 50_000

    /** Hard cap on the `.glb` download size, independent of the face budget. */
    const val MAX_GLB_BYTES: Long = 64L * 1024 * 1024

    fun clamp(maxFaces: Int): Int = maxFaces.coerceIn(MIN_FACES, MAX_FACES)
}

/**
 * True when both the scene face count and (if known) the `.glb` archive face count
 * are within [maxFaces]. This is the client-side half of the double check; the server
 * half is the `max_face_count` search parameter.
 */
fun PokemonModel.fitsBudget(maxFaces: Int): Boolean {
    if (faceCount > maxFaces) return false
    val archiveFaces = glbArchive?.faceCount ?: return true
    return archiveFaces <= maxFaces
}

fun PokemonModel.exceedsDownloadCap(): Boolean =
    (glbArchive?.sizeBytes ?: 0L) > PolygonBudget.MAX_GLB_BYTES
