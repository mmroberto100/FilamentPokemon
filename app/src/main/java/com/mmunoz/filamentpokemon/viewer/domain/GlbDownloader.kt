package com.mmunoz.filamentpokemon.viewer.domain

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import java.io.File

interface GlbDownloader {

    /**
     * Resolves a fresh `.glb` link for [uid] (Sketchfab links expire after ~5 min, so they are
     * never cached) and streams the binary into [destination], reporting [onProgress] along the way.
     * Progress is throttled at the source: one callback per whole percent (per 256 KB when the size
     * is unknown) plus a final one carrying the total byte count.
     *
     * Fails before any byte is transferred when the model has no `.glb` archive
     * ([DataError.Local.NO_GLB_ARCHIVE]) or the declared size exceeds the mobile download cap
     * ([DataError.Local.FILE_TOO_LARGE]). Declared sizes are server-controlled, so the cap is
     * enforced again on the bytes actually received. A body shorter than its declared length, or
     * one that is not a glTF-Binary container, fails with [DataError.Local.CORRUPT_FILE].
     * Transient answers (429/5xx) are retried before the stream starts; a failure mid-stream is not.
     * A failed or cancelled transfer leaves no partial file behind.
     */
    suspend fun download(
        uid: String,
        destination: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<File, DataError>
}
