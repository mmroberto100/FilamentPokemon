package com.mmunoz.filamentpokemon.core.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

class PolygonBudgetTest {

    private fun model(faceCount: Int, glbFaceCount: Int? = faceCount, glbSize: Long = 1_000) = PokemonModel(
        uid = "uid", name = "Pikachu", faceCount = faceCount, vertexCount = 1,
        glbArchive = glbFaceCount?.let { GlbArchive(glbSize, it, 1, 1, 512) },
        thumbnailUrl = null, author = "a", licenseLabel = null, viewerUrl = "",
        isAnimated = false, isDownloadable = true
    )

    @Test
    fun `fits when both scene and archive faces are within budget`() {
        assertThat(model(faceCount = 30_000, glbFaceCount = 30_000).fitsBudget(30_000)).isTrue()
    }

    @Test
    fun `rejects when scene faces exceed budget`() {
        assertThat(model(faceCount = 30_001, glbFaceCount = 10).fitsBudget(30_000)).isFalse()
    }

    @Test
    fun `rejects when archive faces exceed budget even if scene count is lower`() {
        assertThat(model(faceCount = 10_000, glbFaceCount = 40_000).fitsBudget(30_000)).isFalse()
    }

    @Test
    fun `unknown archive falls back to scene face count only`() {
        assertThat(model(faceCount = 29_999, glbFaceCount = null).fitsBudget(30_000)).isTrue()
    }

    @Test
    fun `download cap is enforced on archive size`() {
        assertThat(model(100, glbSize = PolygonBudget.MAX_GLB_BYTES).exceedsDownloadCap()).isFalse()
        assertThat(model(100, glbSize = PolygonBudget.MAX_GLB_BYTES + 1).exceedsDownloadCap()).isTrue()
    }

    @Test
    fun `clamp keeps user threshold inside the allowed range`() {
        assertThat(PolygonBudget.clamp(100)).isEqualTo(PolygonBudget.MIN_FACES)
        assertThat(PolygonBudget.clamp(999_999)).isEqualTo(PolygonBudget.MAX_FACES)
        assertThat(PolygonBudget.clamp(30_000)).isEqualTo(30_000)
    }
}
