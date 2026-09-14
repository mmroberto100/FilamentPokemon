package com.mmunoz.filamentpokemon.viewer.domain

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbHeaderTest {

    @TempDir
    lateinit var tempDir: File

    private fun header(magic: Int = 0x46546C67, version: Int = 2, length: Int): ByteArray =
        ByteBuffer.allocate(GlbHeader.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(magic).putInt(version).putInt(length).array()

    private fun glb(header: ByteArray, payloadBytes: Int): File =
        File(tempDir, "model.glb").apply { writeBytes(header + ByteArray(payloadBytes)) }

    @Test
    fun `accepts a well-formed header whose declared length matches the file`() {
        val file = glb(header(length = 12 + 100), payloadBytes = 100)
        assertThat(GlbHeader.isValid(file)).isTrue()
    }

    @Test
    fun `tolerates trailing bytes after the declared length`() {
        val file = glb(header(length = 12 + 50), payloadBytes = 100)
        assertThat(GlbHeader.isValid(file)).isTrue()
    }

    @Test
    fun `rejects a truncated file`() {
        val file = glb(header(length = 12 + 100), payloadBytes = 40)
        assertThat(GlbHeader.isValid(file)).isFalse()
    }

    @Test
    fun `rejects the wrong magic`() {
        assertThat(GlbHeader.isValid(header(magic = 0x4C4D5848, length = 112), fileLength = 112)).isFalse()
    }

    @Test
    fun `rejects glTF 1 containers`() {
        assertThat(GlbHeader.isValid(header(version = 1, length = 112), fileLength = 112)).isFalse()
    }

    @Test
    fun `rejects a declared length shorter than the header`() {
        assertThat(GlbHeader.isValid(header(length = 4), fileLength = 112)).isFalse()
    }

    @Test
    fun `rejects files shorter than the header and missing files`() {
        assertThat(GlbHeader.isValid(File(tempDir, "short.glb").apply { writeBytes(ByteArray(5)) })).isFalse()
        assertThat(GlbHeader.isValid(File(tempDir, "missing.glb"))).isFalse()
    }

    @Test
    fun `accepts the bundled Khronos sample`() {
        val sample = File("src/debug/assets/models/sample.glb")
        assertThat(GlbHeader.isValid(sample)).isTrue()
    }
}
