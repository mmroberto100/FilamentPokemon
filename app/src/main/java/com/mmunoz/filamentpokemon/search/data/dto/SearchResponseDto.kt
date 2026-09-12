package com.mmunoz.filamentpokemon.search.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val results: List<ModelDto> = emptyList(),
    val cursors: CursorsDto? = null,
    /** Full URL of the next page (also carries the cursor as a query param). */
    val next: String? = null,
    val previous: String? = null
)

@Serializable
data class CursorsDto(
    val next: String? = null,
    val previous: String? = null
)
