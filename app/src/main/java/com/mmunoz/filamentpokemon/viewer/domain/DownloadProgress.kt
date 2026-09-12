package com.mmunoz.filamentpokemon.viewer.domain

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long
) {
    /** 0f..1f; 0f when the total is unknown. */
    val fraction: Float
        get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}
