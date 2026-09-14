package com.mmunoz.filamentpokemon.viewer.data

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.data.networking.RetryPolicy
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.viewer.domain.DownloadProgress
import com.mmunoz.filamentpokemon.viewer.domain.GlbHeader
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(ExperimentalCoroutinesApi::class)
class KtorGlbDownloaderTest {

    @TempDir
    lateinit var tempDir: File

    private val glbBytes = glb(payloadBytes = 200_000)
    private val s3Url = "https://sketchfab-prod-media.s3.amazonaws.com/archives/x/glb/model.glb?X-Amz-Signature=abc"
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    /** The stream runs on a dispatcher of its own, where a virtual-time delay would never elapse. */
    private val noDelay = RetryPolicy(baseDelayMs = 0, maxDelayMs = 0, jitterMs = 0)
    private val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var linkRequest: HttpRequestData? = null
    private val s3Requests = mutableListOf<HttpRequestData>()

    @AfterEach
    fun tearDown() = producerScope.cancel()

    /** A well-formed glTF-Binary: 12-byte header (magic, version 2, total length) plus opaque payload. */
    private fun glb(payloadBytes: Int, magic: Int = 0x46546C67): ByteArray =
        ByteBuffer.allocate(GlbHeader.SIZE_BYTES + payloadBytes).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(magic).putInt(2).putInt(GlbHeader.SIZE_BYTES + payloadBytes)
            .also { buffer -> repeat(payloadBytes) { buffer.put((it % 251).toByte()) } }
            .array()

    private fun linkJson(size: Long = glbBytes.size.toLong(), includeGlb: Boolean = true) = buildString {
        append("""{"usdz":{"url":"https://s3/usdz","size":1,"expires":300}""")
        if (includeGlb) append(""","glb":{"url":"$s3Url","size":$size,"expires":300}""")
        append("}")
    }

    /** [s3Statuses] answers request by request; the last entry repeats. */
    private fun downloader(
        linkStatus: HttpStatusCode = HttpStatusCode.OK,
        linkBody: String = linkJson(),
        s3Statuses: List<HttpStatusCode> = listOf(HttpStatusCode.OK),
        s3Body: ByteArray = glbBytes,
        s3ContentLength: Long? = s3Body.size.toLong(),
        s3Content: () -> ByteReadChannel = { ByteReadChannel(s3Body) },
        s3Throws: Throwable? = null,
        bufferSize: Int = 64 * 1024
    ): KtorGlbDownloader {
        val api = HttpClientFactory.create(
            engine = MockEngine { request ->
                linkRequest = request
                respond(linkBody, linkStatus, jsonHeaders)
            },
            apiToken = "secret-token",
            retryPolicy = noDelay
        )
        val s3 = HttpClientFactory.create(
            engine = MockEngine { request ->
                s3Requests += request
                s3Throws?.let { throw it }
                val status = s3Statuses.getOrElse(s3Requests.lastIndex) { s3Statuses.last() }
                val headers = buildList {
                    add(HttpHeaders.ContentType to listOf("application/octet-stream"))
                    if (status.isSuccess() && s3ContentLength != null) {
                        add(HttpHeaders.ContentLength to listOf(s3ContentLength.toString()))
                    }
                }
                respond(
                    content = if (status.isSuccess()) s3Content() else ByteReadChannel.Empty,
                    status = status,
                    headers = headersOf(*headers.toTypedArray())
                )
            },
            apiToken = "",
            retryPolicy = noDelay
        )
        return KtorGlbDownloader(api, s3, ioDispatcher = UnconfinedTestDispatcher(), bufferSize = bufferSize)
    }

    private val destination get() = File(tempDir, "models/uid123.glb")
    private val partFile get() = File(destination.path + ".part")

    @Test
    fun `happy path streams the glb into the destination and reports progress`() = runTest {
        val progress = mutableListOf<DownloadProgress>()

        val result = downloader().download("uid123", destination) { progress += it }

        assertThat(result).isEqualTo(Result.Success(destination))
        assertThat(destination.readBytes().contentEquals(glbBytes)).isTrue()
        assertThat(partFile.exists()).isFalse()

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
        assertThat(s3Requests.single().url.toString()).isEqualTo(s3Url)
        assertThat(s3Requests.single().headers[HttpHeaders.Authorization]).isNull()
    }

    @Test
    fun `missing glb archive fails before any transfer`() = runTest {
        val result = downloader(linkBody = linkJson(includeGlb = false)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.NO_GLB_ARCHIVE))
        assertThat(s3Requests).isEmpty()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `oversize archive fails before any transfer`() = runTest {
        val result = downloader(linkBody = linkJson(size = PolygonBudget.MAX_GLB_BYTES + 1)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.FILE_TOO_LARGE))
        assertThat(s3Requests).isEmpty()
    }

    @Test
    fun `declared content length over the cap fails before the body is read`() = runTest {
        val result = downloader(s3ContentLength = PolygonBudget.MAX_GLB_BYTES + 1).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.FILE_TOO_LARGE))
        assertThat(partFile.exists()).isFalse()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `a stream that grows past the cap is cut off with FILE_TOO_LARGE`() = runTest {
        val chunk = ByteArray(64 * 1024)
        val plannedBytes = PolygonBudget.MAX_GLB_BYTES + 4 * 1024 * 1024
        var producedBytes = 0L
        val endless = downloader(
            linkBody = linkJson(size = 0),
            s3ContentLength = null,
            s3Content = {
                producerScope.writer {
                    while (producedBytes < plannedBytes) {
                        channel.writeFully(chunk)
                        producedBytes += chunk.size
                    }
                }.channel
            }
        )

        val result = endless.download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.FILE_TOO_LARGE))
        assertThat(producedBytes).isLessThan(plannedBytes)
        assertThat(partFile.exists()).isFalse()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `body shorter than its declared length is CORRUPT_FILE and leaves no file`() = runTest {
        val result = downloader(s3ContentLength = glbBytes.size + 1_000L).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.CORRUPT_FILE))
        assertThat(partFile.exists()).isFalse()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `body without the glTF magic is CORRUPT_FILE and leaves no file`() = runTest {
        val notGlb = glb(payloadBytes = 1_000, magic = 0x4C4D5848)

        val result = downloader(s3Body = notGlb).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.CORRUPT_FILE))
        assertThat(partFile.exists()).isFalse()
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `empty body is CORRUPT_FILE`() = runTest {
        val result = downloader(s3Body = ByteArray(0), s3ContentLength = null).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Local.CORRUPT_FILE))
        assertThat(partFile.exists()).isFalse()
    }

    @Test
    fun `progress is reported once per whole percent, not once per chunk`() = runTest {
        val progress = mutableListOf<DownloadProgress>()
        val chunks = (glbBytes.size + 1023) / 1024

        val result = downloader(bufferSize = 1024).download("uid123", destination) { progress += it }

        assertThat(result).isEqualTo(Result.Success(destination))
        assertThat(chunks).isGreaterThan(150)
        assertThat(progress).hasSize(100)
        assertThat(progress.map { (it.bytesRead * 100 / it.totalBytes).toInt() }).isEqualTo((1..100).toList())
        assertThat(progress.last().bytesRead).isEqualTo(glbBytes.size.toLong())
        assertThat(progress.zipWithNext().all { (a, b) -> b.bytesRead > a.bytesRead }).isTrue()
    }

    @Test
    fun `unknown total reports every 256 KB plus the final byte count`() = runTest {
        val body = glb(payloadBytes = 1_000_000 - GlbHeader.SIZE_BYTES)
        val progress = mutableListOf<DownloadProgress>()

        val result = downloader(linkBody = linkJson(size = 0), s3Body = body, s3ContentLength = null)
            .download("uid123", destination) { progress += it }

        assertThat(result).isEqualTo(Result.Success(destination))
        assertThat(progress).hasSize(4)
        assertThat(progress.all { it.totalBytes == 0L && it.fraction == 0f }).isTrue()
        assertThat(progress.dropLast(1).zipWithNext().all { (a, b) -> b.bytesRead - a.bytesRead >= 256 * 1024 }).isTrue()
        assertThat(progress.last().bytesRead).isEqualTo(1_000_000L)
    }

    @Test
    fun `missing or invalid token surfaces as UNAUTHORIZED`() = runTest {
        val result = downloader(linkStatus = HttpStatusCode.Unauthorized, linkBody = """{"detail":"Authentication credentials were not provided."}""")
            .download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.UNAUTHORIZED))
    }

    @Test
    fun `expired S3 link answers 403 once and leaves no partial file`() = runTest {
        val result = downloader(s3Statuses = listOf(HttpStatusCode.Forbidden)).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.FORBIDDEN))
        assertThat(s3Requests).hasSize(1)
        assertThat(destination.exists()).isFalse()
        assertThat(partFile.exists()).isFalse()
    }

    @Test
    fun `S3 503 is retried and the second answer is streamed`() = runTest {
        val result = downloader(s3Statuses = listOf(HttpStatusCode.ServiceUnavailable, HttpStatusCode.OK))
            .download("uid123", destination)

        assertThat(result).isEqualTo(Result.Success(destination))
        assertThat(s3Requests).hasSize(2)
        assertThat(destination.readBytes().contentEquals(glbBytes)).isTrue()
    }

    @Test
    fun `S3 404 is not retried`() = runTest {
        val result = downloader(s3Statuses = listOf(HttpStatusCode.NotFound, HttpStatusCode.OK))
            .download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
        assertThat(s3Requests).hasSize(1)
    }

    @Test
    fun `a stream that breaks mid-body fails without a second request`() = runTest {
        val broken = downloader(
            s3Content = {
                producerScope.writer {
                    channel.writeFully(glbBytes, 0, 1_000)
                    channel.close(IOException("connection reset"))
                }.channel
            }
        )

        val result = broken.download("uid123", destination)

        assertThat(result).isInstanceOf(Result.Error::class)
        assertThat(s3Requests).hasSize(1)
        assertThat(partFile.exists()).isFalse()
    }

    @Test
    fun `transport failure during the stream maps to NO_INTERNET and cleans up`() = runTest {
        val result = downloader(s3Throws = UnknownHostException("s3")).download("uid123", destination)

        assertThat(result).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(partFile.exists()).isFalse()
    }

    @Test
    fun `an existing file at the destination is replaced`() = runTest {
        destination.parentFile!!.mkdirs()
        destination.writeBytes(ByteArray(3))

        downloader().download("uid123", destination)

        assertThat(destination.length()).isEqualTo(glbBytes.size.toLong())
    }
}
