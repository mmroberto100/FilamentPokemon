package com.mmunoz.filamentpokemon.viewer.domain

import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import java.io.File

interface GlbDownloader {

    /**
     * Resolves a fresh `.glb` link for [uid] (Sketchfab links expire after ~5 min, so they are
     * never cached) and streams the binary into [destination], reporting [onProgress] along the way.
     *
     * Fails before any byte is transferred when the model has no `.glb` archive
     * ([DataError.Local.NO_GLB_ARCHIVE]) or the archive exceeds the mobile download cap
     * ([DataError.Local.FILE_TOO_LARGE]). A failed or cancelled transfer leaves no partial file behind.
     */
    suspend fun download(
        uid: String,
        destination: File,
        onProgress: (DownloadProgress) -> Unit = {}
    ): Result<File, DataError>
}
