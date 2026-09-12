package com.mmunoz.filamentpokemon.viewer.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.data.networking.OkHttpEngineFactory
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.viewer.domain.DownloadProgress
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Downloads a real model through the production engine. Opt-in:
 * `SKETCHFAB_LIVE=1 SKETCHFAB_API_TOKEN=<token> ./gradlew :app:testDebugUnitTest --tests '*GlbDownloaderLiveTest*'`
 */
@EnabledIfEnvironmentVariable(named = "SKETCHFAB_LIVE", matches = "1")
@EnabledIfEnvironmentVariable(named = "SKETCHFAB_API_TOKEN", matches = ".+")
class GlbDownloaderLiveTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `downloads the Pokemon Center glb (612 KB) and the file is a valid glTF binary`() = runTest {
        val token = System.getenv("SKETCHFAB_API_TOKEN")
        val downloader = KtorGlbDownloader(
            apiClient = HttpClientFactory.create(OkHttpEngineFactory.create(), apiToken = token),
            downloadClient = HttpClientFactory.create(OkHttpEngineFactory.create(), apiToken = "")
        )
        val destination = File(tempDir, "models/ae2858d8d212406ebe95927d4f17d328.glb")
        var last: DownloadProgress? = null

        val result = downloader.download("ae2858d8d212406ebe95927d4f17d328", destination) { last = it }

        assertThat(result).isInstanceOf(Result.Success::class)
        assertThat(destination.length()).isEqualTo(612148L)
        assertThat(last!!.fraction).isEqualTo(1f)
        // glTF 2.0 binary header: magic "glTF", version 2, total length.
        val header = destination.inputStream().use { it.readNBytes(12) }
        assertThat(String(header, 0, 4, Charsets.US_ASCII)).isEqualTo("glTF")
        assertThat(header[4].toInt()).isEqualTo(2)
        println("live download OK: ${destination.length()} bytes, progress updates delivered")
    }
}
