package com.mmunoz.filamentpokemon.viewer.data

import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LRU file cache under `<cacheDir>/models`. Recency is tracked with the file's lastModified
 * timestamp, which [get] refreshes. Stale `.part` files from interrupted downloads count
 * towards the budget and are evicted first.
 */
class ModelCacheManager(
    cacheDir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelCache {

    private val modelsDir = File(cacheDir, DIR_NAME)

    override fun fileFor(uid: String): File {
        require(uid.matches(UID_PATTERN)) { "Invalid model uid: $uid" }
        return File(modelsDir, "$uid$GLB_EXTENSION")
    }

    override suspend fun get(uid: String): File? = withContext(ioDispatcher) {
        val file = fileFor(uid)
        if (file.isFile && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            file
        } else {
            null
        }
    }

    override suspend fun evict(uid: String) = withContext(ioDispatcher) {
        val file = fileFor(uid)
        file.delete()
        File(file.path + PART_EXTENSION).delete()
        Unit
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        modelsDir.listFiles()?.forEach { it.delete() }
        Unit
    }

    override suspend fun enforceLimit() = withContext(ioDispatcher) {
        val files = modelsDir.listFiles { f -> f.isFile }?.toMutableList() ?: return@withContext
        var total = files.sumOf { it.length() }
        // Partials first, then least recently used.
        files.sortWith(compareByDescending<File> { it.name.endsWith(PART_EXTENSION) }.thenBy { it.lastModified() })
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) total -= size
        }
    }

    override suspend fun totalBytes(): Long = withContext(ioDispatcher) {
        modelsDir.listFiles { f -> f.isFile }?.sumOf { it.length() } ?: 0L
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 150L * 1024 * 1024
        const val DIR_NAME = "models"
        const val GLB_EXTENSION = ".glb"
        const val PART_EXTENSION = ".part"
        private val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
