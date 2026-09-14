package com.mmunoz.filamentpokemon.viewer.data

import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LRU file cache under `<cacheDir>/models`. Recency is tracked in memory (refreshed by [get])
 * with the file's lastModified timestamp as the fallback for files that predate this process.
 * Stale `.part` files from interrupted downloads count towards the budget and are evicted first.
 *
 * Every lookup and file mutation runs under one mutex, so a lookup never observes a file that a
 * concurrent eviction is halfway through removing. Lease counts (see [ModelCache]) are
 * process-wide: this class is a Koin single.
 */
class ModelCacheManager(
    cacheDir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ModelCache {

    init {
        require(maxBytes > PolygonBudget.MAX_GLB_BYTES) {
            "maxBytes ($maxBytes) must exceed PolygonBudget.MAX_GLB_BYTES (${PolygonBudget.MAX_GLB_BYTES}) " +
                "or a freshly downloaded model could be evicted by enforceLimit()"
        }
    }

    private val modelsDir = File(cacheDir, DIR_NAME)
    private val mutex = Mutex()

    // Only touched while holding [mutex].
    private val leases = HashMap<String, Int>()
    private val lastUsedMillis = HashMap<String, Long>()

    override fun fileFor(uid: String): File {
        requireValidUid(uid)
        return File(modelsDir, "$uid$GLB_EXTENSION")
    }

    override suspend fun get(uid: String): File? {
        val file = fileFor(uid)
        return locked {
            if (file.isFile && file.length() > 0) {
                val now = System.currentTimeMillis()
                lastUsedMillis[uid] = now
                file.setLastModified(now)
                file
            } else {
                null
            }
        }
    }

    override suspend fun acquire(uid: String) {
        requireValidUid(uid)
        mutex.withLock { leases[uid] = (leases[uid] ?: 0) + 1 }
    }

    override suspend fun release(uid: String) {
        val file = fileFor(uid)
        locked {
            val remaining = (leases[uid] ?: return@locked) - 1
            if (remaining > 0) {
                leases[uid] = remaining
            } else {
                leases.remove(uid)
                deleteModel(file)
            }
        }
    }

    override suspend fun evict(uid: String) {
        val file = fileFor(uid)
        locked { deleteModel(file) }
    }

    override suspend fun clear() = locked {
        modelsDir.listFiles()?.forEach { it.delete() }
        lastUsedMillis.clear()
    }

    override suspend fun evictUnused() = locked {
        modelFiles().filterNot { it.isLeased() }.forEach { deleteFile(it) }
    }

    override suspend fun enforceLimit() = locked {
        val files = modelFiles()
        var total = files.sumOf { it.length() }
        // Partials first, then least recently used; leased files are never candidates.
        val candidates = files
            .filterNot { it.isLeased() }
            .sortedWith(compareByDescending<File> { it.isPartial() }.thenBy { it.lastUsed() })
        for (file in candidates) {
            if (total <= maxBytes) break
            val size = file.length()
            if (deleteFile(file)) total -= size
        }
    }

    override suspend fun totalBytes(): Long = locked {
        modelFiles().sumOf { it.length() }
    }

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock {
        withContext(ioDispatcher) { block() }
    }

    private fun requireValidUid(uid: String) = require(uid.matches(UID_PATTERN)) { "Invalid model uid: $uid" }

    private fun modelFiles(): List<File> = modelsDir.listFiles { f -> f.isFile }?.toList() ?: emptyList()

    /** Removes a model's `.glb` together with any `.part` of an interrupted download. */
    private fun deleteModel(glb: File) {
        deleteFile(glb)
        deleteFile(File(glb.path + PART_EXTENSION))
    }

    private fun deleteFile(file: File): Boolean {
        val removed = file.delete()
        if (removed && !file.isPartial()) file.uid()?.let(lastUsedMillis::remove)
        return removed
    }

    private fun File.isPartial(): Boolean = name.endsWith(PART_EXTENSION)

    private fun File.isLeased(): Boolean = uid()?.let { leases.containsKey(it) } ?: false

    private fun File.lastUsed(): Long = uid()?.let { lastUsedMillis[it] } ?: lastModified()

    /** The uid a `<uid>.glb` or `<uid>.glb.part` file belongs to; null for anything else. */
    private fun File.uid(): String? {
        val base = name.removeSuffix(PART_EXTENSION)
        if (!base.endsWith(GLB_EXTENSION)) return null
        return base.removeSuffix(GLB_EXTENSION).takeIf { it.matches(UID_PATTERN) }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 150L * 1024 * 1024
        const val DIR_NAME = "models"
        const val GLB_EXTENSION = ".glb"
        const val PART_EXTENSION = ".part"
        private val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
