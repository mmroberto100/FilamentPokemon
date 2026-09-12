package com.mmunoz.filamentpokemon.viewer.data.dto

import kotlinx.serialization.Serializable

/** `GET /v3/models/{uid}/download` – every entry is optional, only `glb` is used. */
@Serializable
data class DownloadResponseDto(
    val glb: DownloadLinkDto? = null,
    val gltf: DownloadLinkDto? = null,
    val usdz: DownloadLinkDto? = null,
    val source: DownloadLinkDto? = null
)

@Serializable
data class DownloadLinkDto(
    val url: String,
    val size: Long = 0,
    /** Seconds until [url] stops working (Sketchfab returns 300). */
    val expires: Int = 0
)
