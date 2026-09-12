package com.mmunoz.filamentpokemon.core.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferences(
    private val dataStore: DataStore<Preferences>
) : UserPreferences {

    override val maxFaceCount: Flow<Int> = dataStore.data
        .catch { e ->
            // A corrupt/unreadable file falls back to defaults instead of crashing the app.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> PolygonBudget.clamp(prefs[KEY_MAX_FACE_COUNT] ?: PolygonBudget.DEFAULT_MAX_FACES) }
        .distinctUntilChanged()

    override suspend fun setMaxFaceCount(value: Int) {
        dataStore.edit { prefs -> prefs[KEY_MAX_FACE_COUNT] = PolygonBudget.clamp(value) }
    }

    private companion object {
        val KEY_MAX_FACE_COUNT = intPreferencesKey("max_face_count")
    }
}
