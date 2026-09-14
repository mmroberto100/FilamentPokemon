package com.mmunoz.filamentpokemon.viewer.domain

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long
) {
    /** True when the server sent no usable content length; only [bytesRead] is meaningful then. */
    val isIndeterminate: Boolean
        get() = totalBytes <= 0

    /** 0f..1f; 0f when the total is unknown. */
    val fraction: Float
        get() = fractionOrNull ?: 0f

    /** 0f..1f, or `null` when the total is unknown. */
    val fractionOrNull: Float?
        get() = if (isIndeterminate) null else (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
}
