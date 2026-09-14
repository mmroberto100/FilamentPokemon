package com.mmunoz.filamentpokemon.viewer.domain

import java.io.File

/** In-memory lease bookkeeping over a flat directory; mirrors ModelCacheManager's lease semantics. */
class FakeModelCache(private val root: File) : ModelCache {
    val evicted = mutableListOf<String>()
    val acquired = mutableListOf<String>()
    val released = mutableListOf<String>()
    val leases = mutableMapOf<String, Int>()
    var enforceLimitCalls = 0
    var evictUnusedCalls = 0

    override fun fileFor(uid: String): File {
        require(uid.matches(UID_PATTERN)) { "Invalid model uid: $uid" }
        return File(root, "$uid.glb")
    }

    override suspend fun get(uid: String): File? = fileFor(uid).takeIf { it.isFile && it.length() > 0 }

    override suspend fun acquire(uid: String) {
        acquired += uid
        leases[uid] = (leases[uid] ?: 0) + 1
    }

    override suspend fun release(uid: String) {
        released += uid
        val remaining = (leases[uid] ?: return) - 1
        if (remaining > 0) {
            leases[uid] = remaining
        } else {
            leases.remove(uid)
            deleteModel(uid)
        }
    }

    override suspend fun evict(uid: String) {
        evicted += uid
        deleteModel(uid)
    }

    override suspend fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }

    override suspend fun evictUnused() {
        evictUnusedCalls++
        root.listFiles()?.filterNot { leases.containsKey(it.name.removeSuffix(".part").removeSuffix(".glb")) }?.forEach { it.delete() }
    }

    override suspend fun enforceLimit() {
        enforceLimitCalls++
    }

    override suspend fun totalBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L

    private fun deleteModel(uid: String) {
        val file = fileFor(uid)
        file.delete()
        File(file.path + ".part").delete()
    }

    private companion object {
        val UID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
