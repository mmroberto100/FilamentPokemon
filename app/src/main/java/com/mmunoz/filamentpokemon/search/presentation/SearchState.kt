package com.mmunoz.filamentpokemon.search.presentation

import androidx.compose.runtime.Stable
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import com.mmunoz.filamentpokemon.search.domain.PolygonBudget

@Stable
data class SearchState(
    val query: String = "",
    val maxFaceCount: Int = PolygonBudget.DEFAULT_MAX_FACES,
    val models: List<PokemonModelUi> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val nextCursor: String? = null,
    val endReached: Boolean = false,
    val error: UiText? = null
) {
    val canLoadMore: Boolean
        get() = !isLoading && !isLoadingMore && !endReached && nextCursor != null
}

data class PokemonModelUi(
    val uid: String,
    val name: String,
    val author: String,
    val thumbnailUrl: String?,
    val faceCount: Int,
    /** Locale-formatted face count, e.g. "10,041". */
    val formattedFaceCount: String,
    /** faceCount / maxFaceCount – drives the budget chip colour. */
    val budgetUsage: Float,
    val isAnimated: Boolean,
    val licenseLabel: String?
)
