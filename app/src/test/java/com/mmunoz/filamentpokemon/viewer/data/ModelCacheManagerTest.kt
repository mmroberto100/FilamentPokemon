package com.mmunoz.filamentpokemon.viewer.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ModelCacheManagerTest {

    @TempDir
    lateinit var cacheDir: File

    private fun cache(maxBytes: Long = 1_000) =
        ModelCacheManager(cacheDir, maxBytes = maxBytes, ioDispatcher = UnconfinedTestDispatcher())

    private fun write(cache: ModelCacheManager, uid: String, bytes: Int, lastModified: Long): File {
        val file = cache.fileFor(uid)
        file.parentFile!!.mkdirs()
        file.writeBytes(ByteArray(bytes))
        file.setLastModified(lastModified)
        return file
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
    fun `evict removes the model and any partial download`() = runTest {
        val cache = cache()
        val file = write(cache, "a", bytes = 10, lastModified = 1)
        val part = File(file.path + ".part").also { it.writeBytes(ByteArray(5)) }

        cache.evict("a")

        assertThat(file.exists()).isFalse()
        assertThat(part.exists()).isFalse()
    }

    @Test
    fun `enforceLimit deletes least recently used files until under budget`() = runTest {
        val cache = cache(maxBytes = 1_000)
        val oldest = write(cache, "oldest", bytes = 400, lastModified = 1_000)
        val middle = write(cache, "middle", bytes = 400, lastModified = 2_000)
        val newest = write(cache, "newest", bytes = 400, lastModified = 3_000)
        assertThat(cache.totalBytes()).isEqualTo(1_200)

        cache.enforceLimit()

        assertThat(oldest.exists()).isFalse()
        assertThat(middle.exists()).isTrue()
        assertThat(newest.exists()).isTrue()
        assertThat(cache.totalBytes()).isEqualTo(800)
    }

    @Test
    fun `enforceLimit evicts stale partials before complete models`() = runTest {
        val cache = cache(maxBytes = 500)
        val model = write(cache, "model", bytes = 400, lastModified = 1_000)
        val part = File(cache.fileFor("broken").path + ".part").also {
            it.writeBytes(ByteArray(400)); it.setLastModified(5_000)
        }

        cache.enforceLimit()

        assertThat(part.exists()).isFalse()
        assertThat(model.exists()).isTrue()
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
}
