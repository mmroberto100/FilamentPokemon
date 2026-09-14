package com.mmunoz.filamentpokemon.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import co.touchlab.kermit.Logger
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.preferences.UserPreferences
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.EmptyResult
import com.mmunoz.filamentpokemon.core.domain.util.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferences(
    private val dataStore: DataStore<Preferences>
) : UserPreferences {

    private val log = Logger.withTag("UserPreferences")

    override val maxFaceCount: Flow<Int> = dataStore.data
        .catch { e ->
            // A corrupt/unreadable file falls back to defaults instead of crashing the app.
            if (e is IOException) {
                log.w(e) { "Preferences unreadable, using defaults" }
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs -> PolygonBudget.clamp(prefs[KEY_MAX_FACE_COUNT] ?: PolygonBudget.DEFAULT_MAX_FACES) }
        .distinctUntilChanged()

    override suspend fun setMaxFaceCount(value: Int): EmptyResult<DataError.Local> {
        return try {
            dataStore.edit { prefs -> prefs[KEY_MAX_FACE_COUNT] = PolygonBudget.clamp(value) }
            Result.Success(Unit)
        } catch (e: IOException) {
            log.w(e) { "Failed to persist max face count" }
            Result.Error(DataError.Local.UNKNOWN)
        }
    }

    private companion object {
        val KEY_MAX_FACE_COUNT = intPreferencesKey("max_face_count")
    }
}
