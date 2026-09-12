package com.mmunoz.filamentpokemon.viewer.domain

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import java.io.File

class FakeGlbDownloader : GlbDownloader {
    var result: Result<File, DataError>? = null
    var progressSteps = listOf(0.25f, 0.5f, 1f)
    val calls = mutableListOf<String>()

    override suspend fun download(uid: String, destination: File, onProgress: (DownloadProgress) -> Unit): Result<File, DataError> {
        calls += uid
        progressSteps.forEach { onProgress(DownloadProgress((it * 100).toLong(), 100)) }
        return result ?: run {
            destination.parentFile?.mkdirs()
            destination.writeBytes(ByteArray(10))
            Result.Success(destination)
        }
    }
}
