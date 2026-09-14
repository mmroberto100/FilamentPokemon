package com.mmunoz.filamentpokemon.viewer.domain

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The 12-byte glTF-Binary header: magic `glTF`, container version 2 and the total length of the
 * file. Checked before any bytes reach the native gltfio parser; a truncated download or a
 * non-glTF payload fails here instead of inside native code.
 */
object GlbHeader {
    const val SIZE_BYTES = 12
    private const val MAGIC = 0x46546C67
    private const val VERSION = 2

    fun isValid(file: File): Boolean = runCatching {
        val length = file.length()
        if (length < SIZE_BYTES) return false
        val header = ByteArray(SIZE_BYTES)
        FileInputStream(file).use { input ->
            var read = 0
            while (read < SIZE_BYTES) {
                val n = input.read(header, read, SIZE_BYTES - read)
                if (n < 0) return false
                read += n
            }
        }
        isValid(header, length)
    }.getOrDefault(false)

    /** [fileLength] may exceed the declared length (trailing bytes are tolerated, like cgltf does). */
    fun isValid(header: ByteArray, fileLength: Long): Boolean {
        if (header.size < SIZE_BYTES) return false
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val declaredLength = buffer.getInt(8).toLong() and 0xFFFF_FFFFL
        return buffer.getInt(0) == MAGIC &&
            buffer.getInt(4) == VERSION &&
            declaredLength in SIZE_BYTES..fileLength
    }
}
