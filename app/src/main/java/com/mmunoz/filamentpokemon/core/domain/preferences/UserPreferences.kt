package com.mmunoz.filamentpokemon.core.domain.preferences

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.EmptyResult
import kotlinx.coroutines.flow.Flow

interface UserPreferences {
    /** User-tuned triangle budget, always clamped to [PolygonBudget.MIN_FACES]..[PolygonBudget.MAX_FACES]. */
    val maxFaceCount: Flow<Int>

    /** Persists the budget; a write that fails is reported, never thrown. */
    suspend fun setMaxFaceCount(value: Int): EmptyResult<DataError.Local>
}
