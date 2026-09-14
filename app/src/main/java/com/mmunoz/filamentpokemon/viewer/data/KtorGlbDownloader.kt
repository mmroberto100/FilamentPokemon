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
import com.mmunoz.filamentpokemon.viewer.domain.GlbHeader
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE
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
                val contentLength = response.contentLength()
                if (contentLength != null && contentLength > PolygonBudget.MAX_GLB_BYTES) {
                    return@execute Result.Error(DataError.Local.FILE_TOO_LARGE)
                }
                val total = contentLength ?: link.size
                val progress = ProgressThrottle(total, onProgress)
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(bufferSize)
                var bytesRead = 0L

                FileOutputStream(partFile).use { out ->
                    while (true) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        bytesRead += read
                        // Declared sizes are server-controlled; the bytes actually received are the cap.
                        if (bytesRead > PolygonBudget.MAX_GLB_BYTES) {
                            return@execute Result.Error(DataError.Local.FILE_TOO_LARGE)
                        }
                        out.write(buffer, 0, read)
                        progress.onChunk(bytesRead)
                    }
                }
                progress.onComplete(bytesRead)
                if (total > 0 && bytesRead < total) return@execute Result.Error(DataError.Local.CORRUPT_FILE)
                if (!GlbHeader.isValid(partFile)) return@execute Result.Error(DataError.Local.CORRUPT_FILE)

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

    /**
     * Collapses per-chunk updates into one per whole percent – one per [UNKNOWN_TOTAL_STEP_BYTES]
     * when the size is unknown – so a large stream cannot flood the UI with state updates.
     */
    private class ProgressThrottle(
        private val totalBytes: Long,
        private val onProgress: (DownloadProgress) -> Unit
    ) {
        private var lastPercent = 0
        private var lastReportedBytes = 0L

        fun onChunk(bytesRead: Long) {
            if (totalBytes > 0) {
                val percent = (bytesRead * 100 / totalBytes).toInt()
                if (percent == lastPercent) return
                lastPercent = percent
            } else if (bytesRead - lastReportedBytes < UNKNOWN_TOTAL_STEP_BYTES) {
                return
            }
            report(bytesRead)
        }

        /** The final byte count always reaches the caller, unless the last chunk already delivered it. */
        fun onComplete(bytesRead: Long) {
            if (bytesRead != lastReportedBytes) report(bytesRead)
        }

        private fun report(bytesRead: Long) {
            lastReportedBytes = bytesRead
            onProgress(DownloadProgress(bytesRead, totalBytes))
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 64 * 1024
        const val UNKNOWN_TOTAL_STEP_BYTES = 256 * 1024L
        const val DOWNLOAD_REQUEST_TIMEOUT_MS = 10 * 60_000L
        const val DOWNLOAD_SOCKET_TIMEOUT_MS = 60_000L
    }
}
