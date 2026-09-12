package com.mmunoz.filamentpokemon.search.data.dto

import kotlinx.serialization.Serializable

/**
 * Shape shared by `GET /v3/search?type=models` items and `GET /v3/models/{uid}`.
 * Only the fields the app uses are declared; `ignoreUnknownKeys` drops the rest.
 * NOTE: `archives` is only present in search results, never on the single-model endpoint.
 */
@Serializable
data class ModelDto(
    val uid: String,
    val name: String = "",
    val faceCount: Int = 0,
    val vertexCount: Int = 0,
    val isDownloadable: Boolean = false,
    val isAgeRestricted: Boolean = false,
    val animationCount: Int = 0,
    val viewerUrl: String = "",
    val license: LicenseDto? = null,
    val user: UserDto? = null,
    val thumbnails: ThumbnailsDto? = null,
    val archives: ArchivesDto? = null
)

@Serializable
data class ArchivesDto(
    val glb: ArchiveInfoDto? = null,
    val gltf: ArchiveInfoDto? = null,
    val usdz: ArchiveInfoDto? = null,
    val source: ArchiveInfoDto? = null
)

@Serializable
data class ArchiveInfoDto(
    val size: Long = 0,
    val faceCount: Int = 0,
    val vertexCount: Int = 0,
    val textureCount: Int = 0,
    val textureMaxResolution: Int = 0,
    val type: String? = null
)

@Serializable
data class ThumbnailsDto(
    val images: List<ImageDto> = emptyList()
)

@Serializable
data class ImageDto(
    val url: String,
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class UserDto(
    val username: String = "",
    val displayName: String? = null
)

@Serializable
data class LicenseDto(
    val uid: String? = null,
    val label: String? = null
)
