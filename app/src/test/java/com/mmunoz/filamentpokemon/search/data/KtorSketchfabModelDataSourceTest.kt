package com.mmunoz.filamentpokemon.search.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.mmunoz.filamentpokemon.core.data.networking.HttpClientFactory
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.search.data.dto.ArchiveInfoDto
import com.mmunoz.filamentpokemon.search.data.dto.ArchivesDto
import com.mmunoz.filamentpokemon.search.data.dto.CursorsDto
import com.mmunoz.filamentpokemon.search.data.dto.ModelDto
import com.mmunoz.filamentpokemon.search.data.dto.SearchResponseDto
import com.mmunoz.filamentpokemon.testutil.Fixtures
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class KtorSketchfabModelDataSourceTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastRequest: HttpRequestData? = null

    private fun dataSource(status: HttpStatusCode = HttpStatusCode.OK, body: () -> String) =
        KtorSketchfabModelDataSource(
            HttpClientFactory.create(
                engine = MockEngine { request ->
                    lastRequest = request
                    respond(body(), status, jsonHeaders)
                },
                apiToken = ""
            )
        )

    private fun glb(faceCount: Int, size: Long = 1_000) =
        ArchivesDto(glb = ArchiveInfoDto(size = size, faceCount = faceCount))

    private fun dto(
        uid: String,
        faceCount: Int = 1_000,
        archives: ArchivesDto? = glb(faceCount),
        downloadable: Boolean = true,
        ageRestricted: Boolean = false
    ) = ModelDto(
        uid = uid, name = uid, faceCount = faceCount, vertexCount = 1,
        isDownloadable = downloadable, isAgeRestricted = ageRestricted, archives = archives
    )

    private fun page(vararg models: ModelDto, next: String? = null) =
        Json.encodeToString(SearchResponseDto.serializer(), SearchResponseDto(results = models.toList(), cursors = CursorsDto(next = next)))

    @Test
    fun `search sends the mandatory pokemon filters and the face budget`() = runTest {
        val source = dataSource { page() }
        source.search(query = "  charizard ", maxFaceCount = 25_000)

        val url = lastRequest!!.url
        assertThat(url.encodedPath).isEqualTo("/v3/search")
        assertThat(url.parameters["type"]).isEqualTo("models")
        assertThat(url.parameters["q"]).isEqualTo("pokemon charizard")
        assertThat(url.parameters["downloadable"]).isEqualTo("true")
        assertThat(url.parameters["max_face_count"]).isEqualTo("25000")
        assertThat(url.parameters["sort_by"]).isEqualTo("-likeCount")
        assertThat(url.parameters["count"]).isEqualTo("24")
        assertThat(url.parameters["cursor"]).isNull()
    }

    @Test
    fun `empty query searches plain pokemon and cursor is forwarded`() = runTest {
        val source = dataSource { page() }
        source.search(query = "", maxFaceCount = 30_000, cursor = "48")

        assertThat(lastRequest!!.url.parameters["q"]).isEqualTo("pokemon")
        assertThat(lastRequest!!.url.parameters["cursor"]).isEqualTo("48")
    }

    @Test
    fun `search drops models that fail the client-side double check`() = runTest {
        val source = dataSource {
            page(
                dto("ok", faceCount = 30_000),
                dto("scene-over-budget", faceCount = 30_001),
                dto("archive-over-budget", faceCount = 20_000, archives = glb(faceCount = 35_000)),
                dto("no-glb", archives = ArchivesDto(glb = null)),
                dto("no-archives", archives = null),
                dto("not-downloadable", downloadable = false),
                dto("age-restricted", ageRestricted = true),
                next = "24"
            )
        }

        val result = source.search(query = "", maxFaceCount = 30_000)

        val pageResult = (result as Result.Success).data
        assertThat(pageResult.models.map { it.uid }).containsExactly("ok")
        assertThat(pageResult.nextCursor).isEqualTo("24")
    }

    @Test
    fun `search maps the real fixture page`() = runTest {
        val source = dataSource { Fixtures.read("sketchfab/search_page1.json") }
        val result = source.search(query = "", maxFaceCount = 30_000)

        val pageResult = (result as Result.Success).data
        assertThat(pageResult.models.size).isEqualTo(3)
        assertThat(pageResult.models.first().name).isEqualTo("Pokemon RSE - Pokemon Center")
        assertThat(pageResult.nextCursor).isEqualTo("3")
    }

    @Test
    fun `getModel hits the model endpoint and maps without archives`() = runTest {
        val source = dataSource { Fixtures.read("sketchfab/model_ae2858d8.json") }
        val result = source.getModel("ae2858d8d212406ebe95927d4f17d328")

        assertThat(lastRequest!!.url.encodedPath).isEqualTo("/v3/models/ae2858d8d212406ebe95927d4f17d328")
        val model = (result as Result.Success).data
        assertThat(model.faceCount).isEqualTo(10041)
        assertThat(model.glbArchive).isNull()
    }

    @Test
    fun `network errors propagate as typed failures`() = runTest {
        val source = dataSource(status = HttpStatusCode.TooManyRequests) { "" }
        val result = source.search(query = "", maxFaceCount = 30_000)
        assertThat(result).isEqualTo(Result.Error(DataError.Network.TOO_MANY_REQUESTS))

        val malformed = dataSource { "{not json" }
        assertThat(malformed.getModel("x")).isInstanceOf(Result.Error::class)
    }
}
