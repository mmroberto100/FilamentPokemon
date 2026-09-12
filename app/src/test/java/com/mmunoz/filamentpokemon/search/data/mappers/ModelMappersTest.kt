package com.mmunoz.filamentpokemon.search.data.mappers

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.search.data.dto.ArchiveInfoDto
import com.mmunoz.filamentpokemon.search.data.dto.ArchivesDto
import com.mmunoz.filamentpokemon.search.data.dto.ImageDto
import com.mmunoz.filamentpokemon.search.data.dto.ModelDto
import com.mmunoz.filamentpokemon.search.data.dto.SearchResponseDto
import com.mmunoz.filamentpokemon.search.data.dto.UserDto
import com.mmunoz.filamentpokemon.testutil.Fixtures
import org.junit.jupiter.api.Test

class ModelMappersTest {

    @Test
    fun `maps a real search result item`() {
        val page = Fixtures.json.decodeFromString(SearchResponseDto.serializer(), Fixtures.read("sketchfab/search_page1.json"))
        val model = page.results.first().toPokemonModel()

        assertThat(model.uid).isEqualTo("ae2858d8d212406ebe95927d4f17d328")
        assertThat(model.name).isEqualTo("Pokemon RSE - Pokemon Center")
        assertThat(model.faceCount).isEqualTo(10041)
        assertThat(model.vertexCount).isEqualTo(5832)
        assertThat(model.isDownloadable).isTrue()
        assertThat(model.isAnimated).isFalse()
        assertThat(model.author).isEqualTo("Wesai")
        assertThat(model.licenseLabel).isEqualTo("CC Attribution")
        assertThat(model.glbArchive).isNotNull()
        assertThat(model.glbArchive!!.sizeBytes).isEqualTo(612148L)
        assertThat(model.glbArchive!!.faceCount).isEqualTo(10003)
        assertThat(model.glbArchive!!.textureMaxResolution).isEqualTo(128)
        // 720 px is the smallest thumbnail >= preferred width in the fixture.
        val expected720 = page.results.first().thumbnails!!.images.first { it.width == 720 }.url
        assertThat(model.thumbnailUrl).isEqualTo(expected720)
    }

    @Test
    fun `maps the single-model endpoint which has no archives`() {
        val dto = Fixtures.json.decodeFromString(ModelDto.serializer(), Fixtures.read("sketchfab/model_ae2858d8.json"))
        val model = dto.toPokemonModel()

        assertThat(model.uid).isEqualTo("ae2858d8d212406ebe95927d4f17d328")
        assertThat(model.faceCount).isEqualTo(10041)
        assertThat(model.glbArchive).isNull()
        assertThat(model.isDownloadable).isTrue()
    }

    @Test
    fun `thumbnail picks smallest image at or above 720 else the largest`() {
        val wide = listOf(ImageDto("64", 64), ImageDto("1920", 1920), ImageDto("720", 720), ImageDto("1024", 1024))
        assertThat(wide.pickThumbnail()).isEqualTo("720")

        val small = listOf(ImageDto("64", 64), ImageDto("256", 256))
        assertThat(small.pickThumbnail()).isEqualTo("256")

        assertThat(emptyList<ImageDto>().pickThumbnail()).isNull()
    }

    @Test
    fun `author falls back to username when displayName is blank`() {
        val dto = ModelDto(uid = "u", user = UserDto(username = "ash", displayName = " "))
        assertThat(dto.toPokemonModel().author).isEqualTo("ash")
    }

    @Test
    fun `next cursor prefers cursors_next and falls back to parsing the next url`() {
        val fromCursors = SearchResponseDto(cursors = com.mmunoz.filamentpokemon.search.data.dto.CursorsDto(next = "24"))
        assertThat(fromCursors.toSearchPage(emptyList()).nextCursor).isEqualTo("24")

        val fromUrl = SearchResponseDto(next = "https://api.sketchfab.com/v3/search?count=24&cursor=48&q=pokemon")
        assertThat(fromUrl.toSearchPage(emptyList()).nextCursor).isEqualTo("48")

        assertThat(SearchResponseDto().toSearchPage(emptyList()).nextCursor).isNull()
    }

    @Test
    fun `glb archive maps all fields`() {
        val archive = ArchivesDto(glb = ArchiveInfoDto(size = 5, faceCount = 6, vertexCount = 7, textureCount = 8, textureMaxResolution = 9))
        val glb = ModelDto(uid = "u", archives = archive).toPokemonModel().glbArchive!!
        assertThat(glb.sizeBytes).isEqualTo(5L)
        assertThat(glb.faceCount).isEqualTo(6)
        assertThat(glb.vertexCount).isEqualTo(7)
        assertThat(glb.textureCount).isEqualTo(8)
        assertThat(glb.textureMaxResolution).isEqualTo(9)
    }
}
