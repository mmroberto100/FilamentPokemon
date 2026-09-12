package com.mmunoz.filamentpokemon.viewer.domain

import java.io.File

/**
 * Temporary store for downloaded `.glb` files. Implementations must live inside the app's
 * cache directory – never in permanent storage (CLAUDE.md §3).
 *
 * Files are protected by leases: a viewer calls [acquire] before it looks up or downloads a
 * model and [release] when it closes. While at least one lease is held on a uid, neither
 * [release], [enforceLimit] nor [evictUnused] removes its `.glb` or in-flight `.part`; the last
 * [release] deletes both, which keeps the CLAUDE.md §3 rule that a model leaves the device as
 * soon as the last viewer using it closes. [evict] and [clear] deliberately ignore leases – they
 * exist for files the caller knows are unusable – and leave the lease counts untouched.
 */
interface ModelCache {

    /**
     * Deterministic location for [uid]'s model; the file may not exist yet.
     *
     * @throws IllegalArgumentException when [uid] cannot form a safe file name.
     */
    fun fileFor(uid: String): File

    /** The complete cached file for [uid], or `null`. Marks it as recently used. */
    suspend fun get(uid: String): File?

    /** Takes one lease on [uid]; balance every call with [release]. */
    suspend fun acquire(uid: String)

    /**
     * Drops one lease on [uid] and deletes its model and partial download once no lease
     * remains. A release without a matching [acquire] is ignored.
     */
    suspend fun release(uid: String)

    /** Deletes [uid]'s model and partial download even while leased (e.g. a corrupt file). */
    suspend fun evict(uid: String)

    /** Deletes every file, leased or not. */
    suspend fun clear()

    /** Deletes every model and partial download whose uid holds no lease. */
    suspend fun evictUnused()

    /** Deletes least-recently-used unleased models until the cache fits its byte budget. */
    suspend fun enforceLimit()

    suspend fun totalBytes(): Long
}
