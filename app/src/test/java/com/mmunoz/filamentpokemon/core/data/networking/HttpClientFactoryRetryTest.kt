package com.mmunoz.filamentpokemon.core.data.networking

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class HttpClientFactoryRetryTest {

    @Serializable
    private data class Ping(val ok: Boolean)

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val noDelay = RetryPolicy(baseDelayMs = 0, maxDelayMs = 0, jitterMs = 0)
    private val producerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blobUrl = "https://cdn.example.com/model.glb"
    private val blob = ByteArray(300_000) { (it % 251).toByte() }

    private var requests = 0

    @AfterEach
    fun tearDown() = producerScope.cancel()

    private fun client(
        policy: RetryPolicy = noDelay,
        answer: MockRequestHandleScope.(attempt: Int) -> HttpResponseData
    ) = HttpClientFactory.create(
        engine = MockEngine { answer(++requests) },
        apiToken = "",
        retryPolicy = policy
    )

    private fun MockRequestHandleScope.ok() = respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)

    private fun MockRequestHandleScope.status(code: HttpStatusCode, vararg headers: Pair<String, String>) =
        respond("", code, headersOf(*headers.map { (k, v) -> k to listOf(v) }.toTypedArray()))

    private fun MockRequestHandleScope.blob(content: ByteReadChannel = ByteReadChannel(blob)) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/octet-stream")
    )

    @Test
    fun `429 then 200 on a JSON call succeeds after one retry`() = runTest {
        val client = client { attempt -> if (attempt == 1) status(HttpStatusCode.TooManyRequests) else ok() }

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Success(Ping(ok = true)))
        assertThat(requests).isEqualTo(2)
    }

    @Test
    fun `5xx is retried maxRetries times and the last answer is surfaced`() = runTest {
        val client = client { status(HttpStatusCode.ServiceUnavailable) }

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Error(DataError.Network.SERVICE_UNAVAILABLE))
        assertThat(requests).isEqualTo(1 + noDelay.maxRetries)
    }

    @Test
    fun `4xx other than 429 is never retried`() = runTest {
        val client = client { attempt -> if (attempt == 1) status(HttpStatusCode.NotFound) else ok() }

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Error(DataError.Network.NOT_FOUND))
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun `exceptions are never retried`() = runTest {
        val client = client { throw UnknownHostException("api.sketchfab.com") }

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun `Retry-After on a 429 sets the wait before the second request`() = runTest {
        val client = client(policy = RetryPolicy(baseDelayMs = 0, jitterMs = 0)) { attempt ->
            if (attempt == 1) status(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "2") else ok()
        }
        val start = testScheduler.currentTime

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Success(Ping(ok = true)))
        assertThat(requests).isEqualTo(2)
        assertThat(testScheduler.currentTime - start).isEqualTo(2_000L)
    }

    @Test
    fun `Retry-After beyond the cap gives up at once`() = runTest {
        val client = client { attempt ->
            if (attempt == 1) status(HttpStatusCode.TooManyRequests, HttpHeaders.RetryAfter to "60") else ok()
        }

        assertThat(client.get<Ping>(route = "ping")).isEqualTo(Result.Error(DataError.Network.TOO_MANY_REQUESTS))
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun `streamed download reaches the execute block once, with the retried answer`() = runTest {
        val client = client { attempt -> if (attempt == 1) status(HttpStatusCode.ServiceUnavailable) else blob() }
        var executions = 0

        val body = client.prepareGet(blobUrl).execute { response ->
            executions++
            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
            response.bodyAsBytes()
        }

        assertThat(body.contentEquals(blob)).isTrue()
        assertThat(executions).isEqualTo(1)
        assertThat(requests).isEqualTo(2)
    }

    @Test
    fun `streamed download 404 is handed over without a retry`() = runTest {
        val client = client { attempt -> if (attempt == 1) status(HttpStatusCode.NotFound) else blob() }

        val status = client.prepareGet(blobUrl).execute { it.status }

        assertThat(status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun `a body that breaks while streaming is never re-requested`() = runTest {
        val client = client {
            blob(
                producerScope.writer {
                    channel.writeFully(blob, 0, 1_000)
                    channel.close(IOException("connection reset"))
                }.channel
            )
        }

        assertFailure { client.prepareGet(blobUrl).execute { it.bodyAsBytes() } }.isInstanceOf(IOException::class)
        assertThat(requests).isEqualTo(1)
    }
}
