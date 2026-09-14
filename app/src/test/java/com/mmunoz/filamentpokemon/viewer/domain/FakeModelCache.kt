package com.mmunoz.filamentpokemon.viewer.domain

import java.io.File

class FakeModelCache(private val root: File) : ModelCache {
    val evicted = mutableListOf<String>()
    var enforceLimitCalls = 0

    override fun fileFor(uid: String): File = File(root, "$uid.glb")

    override suspend fun get(uid: String): File? = fileFor(uid).takeIf { it.isFile && it.length() > 0 }

    override suspend fun evict(uid: String) {
        evicted += uid
        fileFor(uid).delete()
    }

    override suspend fun clear() {
        root.listFiles()?.forEach { it.delete() }
    }

    override suspend fun enforceLimit() {
        enforceLimitCalls++
    }

    override suspend fun totalBytes(): Long = root.listFiles()?.sumOf { it.length() } ?: 0L
}
