package com.mmunoz.filamentpokemon.viewer.data

import com.mmunoz.filamentpokemon.core.data.networking.get
import com.mmunoz.filamentpokemon.core.data.networking.toNetworkError
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.viewer.data.dto.DownloadLinkDto
import com.mmunoz.filamentpokemon.viewer.data.dto.DownloadResponseDto
import com.mmunoz.filamentpokemon.viewer.domain.DownloadProgress
import com.mmunoz.filamentpokemon.viewer.domain.GlbDownloader
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * @param apiClient      authenticated client for `api.sketchfab.com` (resolves the download link).
 * @param downloadClient **unauthenticated** client for the S3 pre-signed URL – S3 rejects requests
 *                       that carry an `Authorization` header next to a query signature.
 */
class KtorGlbDownloader(
    private val apiClient: HttpClient,
    private val downloadClient: HttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : GlbDownloader {

    override suspend fun download(
        uid: String,
        destination: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File, DataError> {
        val link = when (val links = apiClient.get<DownloadResponseDto>(route = "models/$uid/download")) {
            is Result.Error -> return links
            is Result.Success -> links.data.glb ?: return Result.Error(DataError.Local.NO_GLB_ARCHIVE)
        }
        if (link.size > PolygonBudget.MAX_GLB_BYTES) return Result.Error(DataError.Local.FILE_TOO_LARGE)

        return streamToFile(link, destination, onProgress)
    }

    private suspend fun streamToFile(
        link: DownloadLinkDto,
        destination: File,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File, DataError> = withContext(ioDispatcher) {
        val partFile = File(destination.path + ModelCacheManager.PART_EXTENSION)
        destination.parentFile?.mkdirs()
        partFile.delete()

        try {
            downloadClient.prepareGet(link.url) {
                timeout {
                    requestTimeoutMillis = DOWNLOAD_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = DOWNLOAD_SOCKET_TIMEOUT_MS
                }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    return@execute Result.Error(response.status.value.toDownloadError())
                }
                val total = response.contentLength() ?: link.size
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead = 0L

                FileOutputStream(partFile).use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(DownloadProgress(bytesRead, total))
                    }
                }
                if (bytesRead == 0L) return@execute Result.Error(DataError.Network.UNKNOWN)

                destination.delete()
                if (!partFile.renameTo(destination)) return@execute Result.Error(DataError.Local.UNKNOWN)
                Result.Success(destination)
            }
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: IOException) {
            partFile.delete()
            Result.Error(if (e.isDiskFull()) DataError.Local.DISK_FULL else e.toNetworkError())
        } catch (e: Exception) {
            partFile.delete()
            Result.Error(e.toNetworkError())
        }.also { result -> if (result is Result.Error) partFile.delete() }
    }

    private fun Int.toDownloadError(): DataError.Network = when (this) {
        // An expired pre-signed link answers 403.
        403 -> DataError.Network.FORBIDDEN
        404 -> DataError.Network.NOT_FOUND
        in 500..599 -> DataError.Network.SERVER_ERROR
        else -> DataError.Network.UNKNOWN
    }

    private fun IOException.isDiskFull(): Boolean =
        message?.contains("ENOSPC", ignoreCase = true) == true ||
            message?.contains("No space left", ignoreCase = true) == true

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val DOWNLOAD_REQUEST_TIMEOUT_MS = 10 * 60_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MS = 60_000L
    }
}
