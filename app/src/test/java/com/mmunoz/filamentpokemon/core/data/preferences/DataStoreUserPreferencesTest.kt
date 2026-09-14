package com.mmunoz.filamentpokemon.core.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreUserPreferencesTest {

    @TempDir
    lateinit var tempDir: File

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun preferences(fileName: String = "prefs.preferences_pb") = DataStoreUserPreferences(
        PreferenceDataStoreFactory.create(scope = scope) { File(tempDir, fileName) }
    )

    @AfterEach
    fun tearDown() = scope.cancel()

    @Test
    fun `defaults to the standard budget when nothing is stored`() = runTest {
        assertThat(preferences().maxFaceCount.first()).isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
    }

    @Test
    fun `stores and emits the new value`() = runTest {
        val prefs = preferences()
        prefs.maxFaceCount.test {
            assertThat(awaitItem()).isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
            prefs.setMaxFaceCount(12_000)
            assertThat(awaitItem()).isEqualTo(12_000)
        }
    }

    @Test
    fun `clamps out-of-range values on write`() = runTest {
        val prefs = preferences()
        prefs.setMaxFaceCount(999_999)
        assertThat(prefs.maxFaceCount.first()).isEqualTo(PolygonBudget.MAX_FACES)
        prefs.setMaxFaceCount(1)
        assertThat(prefs.maxFaceCount.first()).isEqualTo(PolygonBudget.MIN_FACES)
    }

    @Test
    fun `value survives a new instance over the same file`() = runTest {
        val file = File(tempDir, "shared.preferences_pb")

        // First "process": write, then release the DataStore by cancelling its scope.
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        DataStoreUserPreferences(PreferenceDataStoreFactory.create(scope = firstScope) { file })
            .setMaxFaceCount(20_000)
        firstScope.cancel()

        // Second "process": a fresh instance over the same file reads the persisted value.
        val second = DataStoreUserPreferences(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertThat(second.maxFaceCount.first()).isEqualTo(20_000)
    }

    @Test
    fun `falls back to the default budget when the stored file is corrupt`() = runTest {
        File(tempDir, "corrupt.preferences_pb").writeBytes("not a protobuf".toByteArray())

        assertThat(preferences("corrupt.preferences_pb").maxFaceCount.first())
            .isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
    }

    @Test
    fun `reports a failed write instead of throwing`() = runTest {
        // A directory where the file should be makes every read and write raise an IOException.
        File(tempDir, "dir.preferences_pb").mkdirs()
        val prefs = preferences("dir.preferences_pb")

        assertThat(prefs.setMaxFaceCount(12_000)).isEqualTo(Result.Error(DataError.Local.PREFERENCE_NOT_SAVED))
        assertThat(prefs.maxFaceCount.first()).isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
    }
}
