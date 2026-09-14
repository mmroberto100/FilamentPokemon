package com.mmunoz.filamentpokemon.core.domain.preferences

import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.EmptyResult
import com.mmunoz.filamentpokemon.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUserPreferences(initial: Int = PolygonBudget.DEFAULT_MAX_FACES) : UserPreferences {
    private val _maxFaceCount = MutableStateFlow(initial)
    override val maxFaceCount: Flow<Int> = _maxFaceCount

    /** When set, every write fails with this error and the stored value is left untouched. */
    var writeError: DataError.Local? = null

    override suspend fun setMaxFaceCount(value: Int): EmptyResult<DataError.Local> {
        writeError?.let { return Result.Error(it) }
        _maxFaceCount.value = PolygonBudget.clamp(value)
        return Result.Success(Unit)
    }
}
