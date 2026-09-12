package com.mmunoz.filamentpokemon.core.domain.preferences

import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferences(initial: Int = PolygonBudget.DEFAULT_MAX_FACES) : UserPreferences {
    private val _maxFaceCount = MutableStateFlow(initial)
    override val maxFaceCount: Flow<Int> = _maxFaceCount

    override suspend fun setMaxFaceCount(value: Int) {
        _maxFaceCount.value = PolygonBudget.clamp(value)
    }
}
