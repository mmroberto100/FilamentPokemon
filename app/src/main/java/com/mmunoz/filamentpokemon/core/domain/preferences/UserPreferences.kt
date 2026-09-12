package com.mmunoz.filamentpokemon.core.domain.preferences

import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    /** User-tuned triangle budget, always clamped to [PolygonBudget.MIN_FACES]..[PolygonBudget.MAX_FACES]. */
    val maxFaceCount: Flow<Int>

    suspend fun setMaxFaceCount(value: Int)
}
