package com.mmunoz.filamentpokemon.viewer.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.viewer.domain.DownloadProgress
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class KtorGlbDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    private val glbBytes = ByteArray(200_000) { (it % 251).toByte() }
    private val s3Url = "https://sketchfab-prod-media.s3.amazonaws.com/archives/x/glb/model.glb?X-Amz-Signature=abc"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private var linkRequest: HttpRequestData? = null
    private var s3Request: HttpRequestData? = null

    private fun linkJson(size: Long = glbBytes.size.toLong(), includeGlb: Boolean = true) = buildString {
        append("""{"usdz":{"url":"https://s3/usdz","size":1,"expires":300}""")
        if (includeGlb) append(""","glb":{"url":"$s3Url","size":$size,"expires":300}""")
        append("}")
    }

    private fun downloader(
        linkStatus: HttpStatusCode = HttpStatusCode.OK,
        linkBody: String = linkJson(),
        s3Status: HttpStatusCode = HttpStatusCode.OK,
        s3Body: ByteArray = glbBytes,
        s3Throws: Throwable? = null
    ): KtorGlbDownloader {
        val api = HttpClientFactory.create(
            engine = MockEngine { request ->
                linkRequest = request
                respond(linkBody, linkStatus, jsonHeaders)
            },
            apiToken = "secret-token"
        )
        val s3 = HttpClientFactory.create(
            engine = MockEngine { request ->
                s3Request = request
                s3Throws?.let { throw it }
                respond(
                    content = ByteReadChannel(s3Body),
                    status = s3Status,
                    headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/octet-stream"),
                        HttpHeaders.ContentLength to listOf(s3Body.size.toString())
                    )
                )
            },
            apiToken = ""
        )
        return KtorGlbDownloader(api, s3, ioDispatcher = UnconfinedTestDispatcher())
    }

    private val destination get() = File(tempDir, "models/uid123.glb")

    @Test
    fun `happy path streams the glb into the destination and reports progress`() = runTest {
        val progress = mutableListOf<DownloadProgress>()

        val result = downloader().download("uid123", destination) { progress += it }

        assertThat(result).isEqualTo(Result.Success(destination))
        assertThat(destination.readBytes().contentEquals(glbBytes)).isTrue()
        assertThat(File(destination.path + ".part").exists()).isFalse()

        assertThat(progress.size).isGreaterThan(1)
        assertThat(progress.last().bytesRead).isEqualTo(glbBytes.size.toLong())
        assertThat(progress.last().totalBytes).isEqualTo(glbBytes.size.toLong())
        assertThat(progress.last().fraction).isEqualTo(1f)
        assertThat(progress.zipWithNext().all { (a, b) -> b.bytesRead > a.bytesRead }).isTrue()
    }

    @Test
    fun `link request is authenticated but the S3 stream never carries the token`() = runTest {
        downloader().download("uid123", destination)

        assertThat(linkRequest!!.url.encodedPath).isEqualTo("/v3/models/uid123/download")
        assertThat(linkRequest!!.headers[HttpHeaders.Authorization]).isEqualTo("Token secret-token")
        assertThat(s3Request!!.url.toString()).isEqualTo(s3Url)
        assertThat(s3Request!!.headers[HttpHeaders.Authorization]).isNull()
    }

    @Test
    fun `missing glb archive fails before any transfer`() = runTest {
        val result = downloader(linkBody = linkJson(includeGlb = false)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.NO_GLB_ARCHIVE))
        assertThat(s3Request).isNull()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `oversize archive fails before any transfer`() = runTest {
        val result = downloader(linkBody = linkJson(size = PolygonBudget.MAX_GLB_BYTES + 1)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.FILE_TOO_LARGE))
        assertThat(s3Request).isNull()
    }

    @Test
    fun `missing or invalid token surfaces as UNAUTHORIZED`() = runTest {
        val result = downloader(linkStatus = HttpStatusCode.Unauthorized, linkBody = """{"detail":"Authentication credentials were not provided."}""")
            .download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }

    @Test
    fun `expired S3 link answers 403 and leaves no partial file`() = runTest {
        val result = downloader(s3Status = HttpStatusCode.Forbidden, s3Body = ByteArray(0)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.FORBIDDEN))
        assertThat(destination.exists()).isFalse()
        assertThat(File(destination.path + ".part").exists()).isFalse()
    }

    @Test
    fun `transport failure during the stream maps to NO_INTERNET and cleans up`() = runTest {
        val result = downloader(s3Throws = UnknownHostException("s3")).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(File(destination.path + ".part").exists()).isFalse()
    }

    @Test
    fun `an existing file at the destination is replaced`() = runTest {
        destination.parentFile!!.mkdirs()
        destination.writeBytes(ByteArray(3))

        downloader().download("uid123", destination)

        assertThat(destination.length()).isEqualTo(glbBytes.size.toLong())
    }
}
