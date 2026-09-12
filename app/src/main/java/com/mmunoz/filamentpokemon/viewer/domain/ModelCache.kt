package com.mmunoz.filamentpokemon.viewer.domain

import java.io.File

/**
 * Temporary store for downloaded `.glb` files. Implementations must live inside the app's
 * cache directory – never in permanent storage (CLAUDE.md §3).
 */
interface ModelCache {

    /** Deterministic location for [uid]'s model; the file may not exist yet. */
    fun fileFor(uid: String): File

    /** The complete cached file for [uid], or `null`. Marks it as recently used. */
    suspend fun get(uid: String): File?

    suspend fun evict(uid: String)

    suspend fun clear()

    /** Deletes least-recently-used models until the cache fits its byte budget. */
    suspend fun enforceLimit()

    suspend fun totalBytes(): Long
}
