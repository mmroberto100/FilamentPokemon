package com.mmunoz.filamentpokemon.search.data

import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.search.domain.PolygonBudget
import com.mmunoz.filamentpokemon.search.domain.fitsBudget
import com.mmunoz.filamentpokemon.core.data.networking.OkHttpEngineFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Hits the real Sketchfab API through the production OkHttp client.
 * Opt-in only: `SKETCHFAB_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*SketchfabLiveApiTest*'`
 */
@EnabledIfEnvironmentVariable(named = "SKETCHFAB_LIVE", matches = "1")
class SketchfabLiveApiTest {

    private val dataSource = KtorSketchfabModelDataSource(
        HttpClientFactory.create(engine = OkHttpEngineFactory.create(), apiToken = "")
    )

    @Test
    fun `live search returns only downloadable glb models within the default budget`() = runTest {
        val result = dataSource.search(query = "pikachu", maxFaceCount = PolygonBudget.DEFAULT_MAX_FACES)

        assertThat(result).isInstanceOf(Result.Success::class)
        val page = (result as Result.Success).data
        assertThat(page.models.size).isGreaterThan(0)
        assertThat(page.nextCursor).isNotNull()
        assertThat(page.models.all { it.isDownloadable && it.glbArchive != null }).isTrue()
        assertThat(page.models.all { it.fitsBudget(PolygonBudget.DEFAULT_MAX_FACES) }).isTrue()
        println("live search: ${page.models.size} models, e.g. " +
            page.models.take(3).joinToString { "${it.name} (${it.faceCount} faces)" })
    }

    @Test
    fun `live getModel returns metadata`() = runTest {
        val result = dataSource.getModel("ae2858d8d212406ebe95927d4f17d328")
        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat((result as Result.Success).data.faceCount).isGreaterThan(0)
    }
}
