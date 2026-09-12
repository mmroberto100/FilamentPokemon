package com.mmunoz.filamentpokemon.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SafeCallTest {

    @Serializable
    private data class Ping(val ok: Boolean)

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun clientReturning(status: HttpStatusCode, body: String = "", token: String = "") =
        HttpClientFactory.create(
            engine = MockEngine { respond(body, status, jsonHeaders) },
            apiToken = token
        )

    @Test
    fun `2xx body is deserialized into Success`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"ok":true,"extra":"ignored"}""")
        val result = client.get<Ping>(route = "ping")
        assertThat(result).isEqualTo(Result.Success(Ping(ok = true)))
    }

    @Test
    fun `malformed body maps to SERIALIZATION`() = runTest {
        val client = clientReturning(HttpStatusCode.OK, """{"ok":"not-a-boolean"}""")
        val result = client.get<Ping>(route = "ping")
        assertThat(result).isEqualTo(Result.Error(DataError.Network.SERIALIZATION))
    }

    @ParameterizedTest(name = "HTTP {0} -> {1}")
    @CsvSource(
        "400, BAD_REQUEST",
        "401, UNAUTHORIZED",
        "403, FORBIDDEN",
        "404, NOT_FOUND",
        "408, REQUEST_TIMEOUT",
        "409, CONFLICT",
        "413, PAYLOAD_TOO_LARGE",
        "429, TOO_MANY_REQUESTS",
        "500, SERVER_ERROR",
        "502, SERVER_ERROR",
        "503, SERVICE_UNAVAILABLE",
        "418, UNKNOWN"
    )
    fun `http status maps to typed error`(status: Int, expected: DataError.Network) = runTest {
        val client = clientReturning(HttpStatusCode.fromValue(status))
        val result = client.get<Ping>(route = "ping")
        assertThat(result).isEqualTo(Result.Error(expected))
    }

    @Test
    fun `dns failure maps to NO_INTERNET`() = runTest {
        val client = HttpClientFactory.create(
            engine = MockEngine { throw UnknownHostException("api.sketchfab.com") },
            apiToken = ""
        )
        assertThat(client.get<Ping>(route = "ping"))
            .isEqualTo(Result.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `socket timeout maps to REQUEST_TIMEOUT`() = runTest {
        val client = HttpClientFactory.create(
            engine = MockEngine { throw SocketTimeoutException("read timed out") },
            apiToken = ""
        )
        assertThat(client.get<Ping>(route = "ping"))
            .isEqualTo(Result.Error(DataError.Network.REQUEST_TIMEOUT))
    }

    @Test
    fun `relative route resolves against Sketchfab base URL and null params are skipped`() = runTest {
        var requestedUrl = ""
        val client = HttpClientFactory.create(
            engine = MockEngine { request ->
                requestedUrl = request.url.toString()
                respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
            },
            apiToken = ""
        )
        client.get<Ping>(
            route = "search",
            queryParameters = mapOf("q" to "pokemon", "cursor" to null, "max_face_count" to 30_000)
        )
        assertThat(requestedUrl)
            .isEqualTo("https://api.sketchfab.com/v3/search?q=pokemon&max_face_count=30000")
    }

    @Test
    fun `api token is sent as Token header only when present`() = runTest {
        var authHeader: String? = null
        fun engine() = MockEngine { request ->
            authHeader = request.headers[HttpHeaders.Authorization]
            respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
        }

        HttpClientFactory.create(engine(), apiToken = "abc123").get<Ping>(route = "me")
        assertThat(authHeader).isEqualTo("Token abc123")

        HttpClientFactory.create(engine(), apiToken = "").get<Ping>(route = "me")
        assertThat(authHeader).isNull()
    }

    @Test
    fun `constructRoute handles relative, slash-prefixed and absolute routes`() {
        assertThat(constructRoute("search")).isEqualTo("https://api.sketchfab.com/v3/search")
        assertThat(constructRoute("/search")).isEqualTo("https://api.sketchfab.com/v3/search")
        assertThat(constructRoute("https://cdn.example.com/model.glb"))
            .isEqualTo("https://cdn.example.com/model.glb")
    }
}
