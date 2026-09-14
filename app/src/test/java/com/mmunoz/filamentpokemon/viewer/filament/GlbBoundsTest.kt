package com.mmunoz.filamentpokemon.viewer.filament

import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbBoundsTest {

    private fun asset(name: String): ByteBuffer = ByteBuffer.wrap(File("src/debug/assets/models/$name").readBytes())

    private fun glb(json: String): ByteBuffer {
        val padded = json.padEnd((json.length + 3) / 4 * 4, ' ').toByteArray()
        return ByteBuffer.allocate(20 + padded.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46546C67).putInt(2).putInt(20 + padded.size)
            .putInt(padded.size).putInt(0x4E4F534A).put(padded)
            .apply { flip() }
    }

    @Test
    fun `float positions need no correction`() {
        assertThat(GlbBounds.correctedBox(asset("duck.glb"))).isNull()
        assertThat(GlbBounds.correctedBox(asset("duck_draco.glb"))).isNull()
        assertThat(GlbBounds.correctedBox(asset("sample.glb"))).isNull()
    }

    @Test
    fun `quantized duck gets the plain duck's bounds back`() {
        // Measured on device for duck.glb: centre [0.1344, 0.8695, -0.0370], half extent [0.8274, 0.7702, 0.5763].
        val box = GlbBounds.correctedBox(asset("duck_meshopt.glb"))
        assertThat(box).isNotNull()
        val tolerance = 0.002f
        assertThat(box!!.center[0]).isCloseTo(0.1344f, tolerance)
        assertThat(box.center[1]).isCloseTo(0.8695f, tolerance)
        assertThat(box.center[2]).isCloseTo(-0.0370f, tolerance)
        assertThat(box.halfExtent[0]).isCloseTo(0.8274f, tolerance)
        assertThat(box.halfExtent[1]).isCloseTo(0.7702f, tolerance)
        assertThat(box.halfExtent[2]).isCloseTo(0.5763f, tolerance)
    }

    @Test
    fun `dequantizes then applies the node hierarchy`() {
        // UBYTE positions 0..255 normalize to 0..1; child translates by (1,2,3); parent scales by 2.
        val box = GlbBounds.correctedBox(
            glb(
                """{"scenes":[{"nodes":[0]}],
                    "nodes":[{"scale":[2,2,2],"children":[1]},{"translation":[1,2,3],"mesh":0}],
                    "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                    "accessors":[{"componentType":5121,"normalized":true,"count":8,"type":"VEC3","min":[0,0,0],"max":[255,255,255]}]}"""
            )
        )
        assertThat(box).isNotNull()
        assertThat(box!!.min[0]).isCloseTo(2f, 1e-5f)
        assertThat(box.min[1]).isCloseTo(4f, 1e-5f)
        assertThat(box.min[2]).isCloseTo(6f, 1e-5f)
        assertThat(box.max[0]).isCloseTo(4f, 1e-5f)
        assertThat(box.max[1]).isCloseTo(6f, 1e-5f)
        assertThat(box.max[2]).isCloseTo(8f, 1e-5f)
    }

    @Test
    fun `honours a node matrix and parentless nodes outside any scene`() {
        // No scenes at all: the parentless node is still a root. Matrix = translate (0, 0, -5).
        val box = GlbBounds.correctedBox(
            glb(
                """{"nodes":[{"matrix":[1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,-5,1],"mesh":0}],
                    "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                    "accessors":[{"componentType":5122,"normalized":true,"count":8,"type":"VEC3","min":[-32767,-32767,-32767],"max":[32767,32767,32767]}]}"""
            )
        )
        assertThat(box).isNotNull()
        assertThat(box!!.center[2]).isCloseTo(-5f, 1e-5f)
        assertThat(box.halfExtent[0]).isCloseTo(1f, 1e-5f)
    }

    @Test
    fun `unreadable input yields null instead of throwing`() {
        assertThat(GlbBounds.correctedBox(ByteBuffer.wrap(ByteArray(40)))).isNull()
        assertThat(GlbBounds.correctedBox(glb("{not json"))).isNull()
        assertThat(GlbBounds.correctedBox(asset("corrupt.glb"))).isNull()
    }

    @Test
    fun `a node that repeats itself as its own child is visited once`() {
        // A hostile tree: node 0 lists itself many times; a naive walk would fan out exponentially.
        val box = GlbBounds.correctedBox(
            glb(
                """{"scenes":[{"nodes":[0]}],
                    "nodes":[{"children":[0,0,0,0,0,0,0,0],"mesh":0}],
                    "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}],
                    "accessors":[{"componentType":5122,"normalized":true,"count":8,"type":"VEC3","min":[-32767,-32767,-32767],"max":[32767,32767,32767]}]}"""
            )
        )
        assertThat(box).isNotNull()
        assertThat(box!!.halfExtent[0]).isCloseTo(1f, 1e-5f)
    }
}
