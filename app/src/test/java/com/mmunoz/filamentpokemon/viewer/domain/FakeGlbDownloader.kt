package com.mmunoz.filamentpokemon.viewer.domain

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes a well-formed glTF-Binary container by default (the ViewModel validates the header
 * before rendering) and replays [progress] verbatim, which lets a test report an unknown total.
 */
class FakeGlbDownloader : GlbDownloader {
    var result: Result<File, DataError>? = null
    var bytes: ByteArray = validGlb()
    var progress = listOf(DownloadProgress(25, 100), DownloadProgress(50, 100), DownloadProgress(100, 100))
    val calls = mutableListOf<String>()

    override suspend fun download(uid: String, destination: File, onProgress: (DownloadProgress) -> Unit): Result<File, DataError> {
        calls += uid
        progress.forEach(onProgress)
        return result ?: run {
            destination.parentFile?.mkdirs()
            destination.writeBytes(bytes)
            Result.Success(destination)
        }
    }
}

/** A minimal glTF-Binary container: valid header followed by [payloadBytes] zero bytes. */
fun validGlb(payloadBytes: Int = 100): ByteArray =
    ByteBuffer.allocate(GlbHeader.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        .putInt(0x46546C67).putInt(2).putInt(GlbHeader.SIZE_BYTES + payloadBytes)
        .array() + ByteArray(payloadBytes)
