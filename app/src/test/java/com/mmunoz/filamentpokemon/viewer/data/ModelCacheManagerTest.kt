package com.mmunoz.filamentpokemon.viewer.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheManagerTest {

    @TempDir
    lateinit var cacheDir: File

    private fun cache(maxBytes: Long = BUDGET, ioDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
        ModelCacheManager(cacheDir, maxBytes = maxBytes, ioDispatcher = ioDispatcher)

    /** Sparse files: model-sized on paper, free on disk. */
    private fun write(cache: ModelCacheManager, uid: String, bytes: Long, lastModified: Long): File =
        cache.fileFor(uid).also { sparse(it, bytes, lastModified) }

    private fun partFor(cache: ModelCacheManager, uid: String, bytes: Long, lastModified: Long): File =
        File(cache.fileFor(uid).path + ModelCacheManager.PART_EXTENSION).also { sparse(it, bytes, lastModified) }

    private fun sparse(file: File, bytes: Long, lastModified: Long) {
        file.parentFile!!.mkdirs()
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
        file.setLastModified(lastModified)
    }

    @Test
    fun `files live under cacheDir models with the uid as name`() {
        val file = cache().fileFor("abc123")
        assertThat(file).isEqualTo(File(cacheDir, "models/abc123.glb"))
    }

    @Test
    fun `rejects uids that could escape the cache directory`() {
        assertThrows<IllegalArgumentException> { cache().fileFor("../../etc/passwd") }
        assertThrows<IllegalArgumentException> { cache().fileFor("") }
    }

    @Test
    fun `rejects a byte budget that could not hold one maximum-size download`() {
        val error = assertThrows<IllegalArgumentException> { cache(maxBytes = PolygonBudget.MAX_GLB_BYTES) }
        assertThat(error).messageContains("MAX_GLB_BYTES")
        assertThrows<IllegalArgumentException> { cache(maxBytes = 1_000) }
        cache(maxBytes = PolygonBudget.MAX_GLB_BYTES + 1)
    }

    @Test
    fun `get returns null for missing or empty files`() = runTest {
        val cache = cache()
        assertThat(cache.get("missing")).isNull()

        val empty = cache.fileFor("empty").also { it.parentFile!!.mkdirs(); it.writeBytes(ByteArray(0)) }
        assertThat(empty.exists()).isTrue()
        assertThat(cache.get("empty")).isNull()
    }

    @Test
    fun `get returns the file and refreshes its recency`() = runTest {
        val cache = cache()
        val old = System.currentTimeMillis() - 60_000
        write(cache, "a", bytes = 10, lastModified = old)

        val hit = cache.get("a")

        assertThat(hit).isNotNull()
        assertThat(hit!!.lastModified()).isGreaterThan(old)
    }

    @Test
    fun `recency from get outranks an older timestamp on disk`() = runTest {
        val cache = cache()
        val touched = write(cache, "touched", bytes = 30 * MB, lastModified = 1_000)
        val untouched = write(cache, "untouched", bytes = 30 * MB, lastModified = 2_000)
        val newest = write(cache, "newest", bytes = 30 * MB, lastModified = 3_000)
        cache.get("touched")
        // A filesystem that ignores setLastModified would still report the stale timestamp.
        touched.setLastModified(1_000)

        cache.enforceLimit()

        assertThat(untouched.exists()).isFalse()
        assertThat(touched.exists()).isTrue()
        assertThat(newest.exists()).isTrue()
    }

    @Test
    fun `evict removes the model and any partial download`() = runTest {
        val cache = cache()
        val file = write(cache, "a", bytes = 10, lastModified = 1)
        val part = partFor(cache, "a", bytes = 5, lastModified = 1)

        cache.evict("a")

        assertThat(file.exists()).isFalse()
        assertThat(part.exists()).isFalse()
    }

    @Test
    fun `evict ignores leases`() = runTest {
        val cache = cache()
        val file = write(cache, "a", bytes = 10, lastModified = 1)
        cache.acquire("a")

        cache.evict("a")

        assertThat(file.exists()).isFalse()
    }

    @Test
    fun `enforceLimit deletes least recently used files until under budget`() = runTest {
        val cache = cache()
        val oldest = write(cache, "oldest", bytes = 30 * MB, lastModified = 1_000)
        val middle = write(cache, "middle", bytes = 30 * MB, lastModified = 2_000)
        val newest = write(cache, "newest", bytes = 30 * MB, lastModified = 3_000)
        assertThat(cache.totalBytes()).isEqualTo(90 * MB)

        cache.enforceLimit()

        assertThat(oldest.exists()).isFalse()
        assertThat(middle.exists()).isTrue()
        assertThat(newest.exists()).isTrue()
        assertThat(cache.totalBytes()).isEqualTo(60 * MB)
    }

    @Test
    fun `enforceLimit evicts stale partials before complete models`() = runTest {
        val cache = cache()
        val model = write(cache, "model", bytes = 50 * MB, lastModified = 1_000)
        val part = partFor(cache, "broken", bytes = 50 * MB, lastModified = 5_000)

        cache.enforceLimit()

        assertThat(part.exists()).isFalse()
        assertThat(model.exists()).isTrue()
    }

    @Test
    fun `enforceLimit skips leased files even when they are the oldest`() = runTest {
        val cache = cache()
        val leased = write(cache, "leased", bytes = 50 * MB, lastModified = 1_000)
        val leasedPart = partFor(cache, "inflight", bytes = 30 * MB, lastModified = 1_000)
        val unleased = write(cache, "unleased", bytes = 50 * MB, lastModified = 9_000)
        cache.acquire("leased")
        cache.acquire("inflight")

        cache.enforceLimit()

        assertThat(leased.exists()).isTrue()
        assertThat(leasedPart.exists()).isTrue()
        assertThat(unleased.exists()).isFalse()
    }

    @Test
    fun `enforceLimit leaves an over-budget cache alone when every file is leased`() = runTest {
        val cache = cache()
        val a = write(cache, "a", bytes = 50 * MB, lastModified = 1_000)
        val b = write(cache, "b", bytes = 50 * MB, lastModified = 2_000)
        cache.acquire("a")
        cache.acquire("b")

        cache.enforceLimit()

        assertThat(a.exists()).isTrue()
        assertThat(b.exists()).isTrue()
    }

    @Test
    fun `release deletes the files only when the last lease is dropped`() = runTest {
        val cache = cache()
        val file = write(cache, "a", bytes = 10, lastModified = 1)
        val part = partFor(cache, "a", bytes = 5, lastModified = 1)
        cache.acquire("a")
        cache.acquire("a")

        cache.release("a")
        assertThat(file.exists()).isTrue()
        assertThat(part.exists()).isTrue()

        cache.release("a")
        assertThat(file.exists()).isFalse()
        assertThat(part.exists()).isFalse()
    }

    @Test
    fun `release without a lease is a no-op`() = runTest {
        val cache = cache()
        val file = write(cache, "a", bytes = 10, lastModified = 1)

        cache.release("a")

        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `evictUnused deletes only files without a lease`() = runTest {
        val cache = cache()
        val leased = write(cache, "leased", bytes = 10, lastModified = 1)
        val leasedPart = partFor(cache, "leased", bytes = 5, lastModified = 1)
        val idle = write(cache, "idle", bytes = 10, lastModified = 1)
        val idlePart = partFor(cache, "abandoned", bytes = 5, lastModified = 1)
        cache.acquire("leased")

        cache.evictUnused()

        assertThat(leased.exists()).isTrue()
        assertThat(leasedPart.exists()).isTrue()
        assertThat(idle.exists()).isFalse()
        assertThat(idlePart.exists()).isFalse()
    }

    @Test
    fun `clear removes everything`() = runTest {
        val cache = cache()
        write(cache, "a", 10, 1)
        write(cache, "b", 10, 2)

        cache.clear()

        assertThat(cache.totalBytes()).isEqualTo(0)
        assertThat(cache.get("a")).isNull()
    }

    @Test
    fun `file operations from many threads never run at the same time`() = runTest {
        val probe = ConcurrencyProbe()
        val cache = cache(ioDispatcher = probe)
        val uids = List(8) { "uid$it" }
        val rounds = 4

        withContext(Dispatchers.Default) {
            repeat(rounds) {
                uids.map { uid ->
                    launch {
                        cache.acquire(uid)
                        write(cache, uid, bytes = 1_000, lastModified = 1)
                        cache.get(uid)
                        cache.enforceLimit()
                        cache.release(uid)
                        cache.evict(uid)
                    }
                }.joinAll()
            }
        }

        assertThat(probe.runs.get()).isEqualTo(uids.size * rounds * 4)
        assertThat(probe.maxConcurrent.get()).isEqualTo(1)
    }

    /**
     * Runs blocks on real IO threads and records how many of the cache's `withContext(io)` sections
     * overlap. A section counts from its dispatch until its job completes — not until the dispatched
     * runnable returns, because that runnable also resumes the caller (which releases the mutex), so
     * on a slow machine the next section can legitimately start before the runnable unwinds.
     */
    private class ConcurrencyProbe : CoroutineDispatcher() {
        private val active = AtomicInteger()
        private val tracked = ConcurrentHashMap.newKeySet<Job>()
        val maxConcurrent = AtomicInteger()
        val runs = AtomicInteger()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val job = checkNotNull(context[Job]) { "withContext sections always carry a Job" }
            if (tracked.add(job)) {
                val now = active.incrementAndGet()
                maxConcurrent.accumulateAndGet(now) { a, b -> maxOf(a, b) }
                runs.incrementAndGet()
                job.invokeOnCompletion { active.decrementAndGet() }
            }
            Dispatchers.IO.dispatch(context) {
                // Widens the window so unserialized sections would overlap.
                Thread.sleep(2)
                block.run()
            }
        }
    }

    private companion object {
        const val MB = 1024L * 1024
        val BUDGET = PolygonBudget.MAX_GLB_BYTES + 16 * MB // 80 MB: three 30 MB models overflow it
    }
}
