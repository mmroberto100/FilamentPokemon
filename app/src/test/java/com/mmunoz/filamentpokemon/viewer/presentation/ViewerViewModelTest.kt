package com.mmunoz.filamentpokemon.viewer.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.domain.preferences.FakeUserPreferences
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import com.mmunoz.filamentpokemon.search.domain.FakeSketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.domain.FakeSketchfabModelDataSource.Companion.model
import com.mmunoz.filamentpokemon.viewer.domain.DownloadProgress
import com.mmunoz.filamentpokemon.viewer.domain.FakeGlbDownloader
import com.mmunoz.filamentpokemon.viewer.domain.FakeModelCache
import com.mmunoz.filamentpokemon.viewer.domain.validGlb
import com.mmunoz.filamentpokemon.viewer.filament.ModelLoadError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataSource: FakeSketchfabModelDataSource
    private lateinit var downloader: FakeGlbDownloader
    private lateinit var cache: FakeModelCache
    private lateinit var preferences: FakeUserPreferences

    private val uid = "abc123"

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataSource = FakeSketchfabModelDataSource().apply {
            modelResult = Result.Success(model(uid, faceCount = 10_000, name = "Pikachu"))
        }
        downloader = FakeGlbDownloader()
        cache = FakeModelCache(tempDir)
        preferences = FakeUserPreferences(initial = 30_000)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(cleanupScope: TestScope? = null, uid: String = this.uid) = ViewerViewModel(
        savedStateHandle = SavedStateHandle(mapOf("uid" to uid, "name" to "Pikachu (route)")),
        modelDataSource = dataSource,
        downloader = downloader,
        cache = cache,
        userPreferences = preferences,
        cleanupScope = cleanupScope ?: TestScope(testDispatcher),
        ioDispatcher = testDispatcher
    )

    private fun errorRes(state: ViewerState) = (state.error as UiText.StringResource).id

    private fun cacheFile(bytes: ByteArray = validGlb()): File =
        cache.fileFor(uid).apply { parentFile?.mkdirs(); writeBytes(bytes) }

    /** Simulates Compose Navigation clearing the destination's ViewModel. */
    private fun clear(vm: ViewerViewModel) = ViewModelStore().apply { put("viewer", vm) }.clear()

    @Test
    fun `happy path downloads with progress, hands the file to the renderer, then becomes Ready`() = runTest {
        val vm = viewModel()

        val state = vm.state.value
        assertThat(state.phase).isEqualTo(ViewerPhase.LoadingIntoScene)
        assertThat(state.modelFile).isEqualTo(cache.fileFor(uid))
        assertThat(state.downloadProgress).isEqualTo(1f)
        assertThat(state.name).isEqualTo("Pikachu")
        assertThat(state.info).isNotNull()
        assertThat(state.info!!.formattedFaceCount).isEqualTo("10,000")
        assertThat(downloader.calls).containsExactly(uid)
        assertThat(cache.enforceLimitCalls).isEqualTo(1)
        assertThat(cache.leases[uid]).isEqualTo(1)

        vm.onAction(ViewerAction.OnModelLoaded)
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Ready)
    }

    @Test
    fun `download progress is reflected in state`() = runTest {
        downloader.progress = listOf(DownloadProgress(40, 100))
        downloader.result = Result.Error(DataError.Network.NO_INTERNET) // stop after progress
        val vm = viewModel()

        // The last progress emitted before the failure stays visible on the failed state.
        assertThat(vm.state.value.downloadProgress).isEqualTo(0.4f)
    }

    @Test
    fun `unknown download size yields indeterminate progress`() = runTest {
        downloader.progress = listOf(DownloadProgress(bytesRead = 40_000, totalBytes = -1))
        downloader.result = Result.Error(DataError.Network.NO_INTERNET) // stop after progress
        val vm = viewModel()

        assertThat(vm.state.value.downloadProgress).isNull()
    }

    @Test
    fun `models over the triangle budget are rejected before any download`() = runTest {
        dataSource.modelResult = Result.Success(model(uid, faceCount = 40_000))
        val vm = viewModel()

        val state = vm.state.value
        assertThat(state.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(state)).isEqualTo(R.string.error_over_polygon_budget)
        assertThat(downloader.calls).hasSize(0)
        assertThat(state.modelFile).isNull()
    }

    @Test
    fun `budget gate uses the user's stored threshold`() = runTest {
        preferences = FakeUserPreferences(initial = 8_000)
        val vm = viewModel() // model has 10,000 faces

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(downloader.calls).hasSize(0)
    }

    @Test
    fun `non-downloadable models are rejected before any download`() = runTest {
        dataSource.modelResult = Result.Success(model(uid).copy(isDownloadable = false))
        val vm = viewModel()

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(downloader.calls).hasSize(0)
    }

    @Test
    fun `cached file skips the download entirely`() = runTest {
        cacheFile()
        val vm = viewModel()

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)
        assertThat(vm.state.value.modelFile).isEqualTo(cache.fileFor(uid))
        assertThat(downloader.calls).hasSize(0)
    }

    @Test
    fun `a cached file with a corrupt header is evicted and reported instead of rendered`() = runTest {
        cacheFile(ByteArray(40))
        val vm = viewModel()

        val state = vm.state.value
        assertThat(state.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(state)).isEqualTo(R.string.error_corrupt_file)
        assertThat(state.modelFile).isNull()
        assertThat(cache.evicted).containsExactly(uid)
        assertThat(cache.fileFor(uid).exists()).isFalse()
        assertThat(downloader.calls).hasSize(0)
    }

    @Test
    fun `a download with a corrupt header is evicted and reported instead of rendered`() = runTest {
        downloader.bytes = ByteArray(40)
        val vm = viewModel()

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_corrupt_file)
        assertThat(cache.evicted).containsExactly(uid)
        assertThat(cache.fileFor(uid).exists()).isFalse()
    }

    @Test
    fun `a uid that cannot name a cache file fails without touching the network or the cache`() = runTest {
        val vm = viewModel(uid = "../../etc/passwd")

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_file_not_found)
        assertThat(vm.state.value.info).isNull() // metadata was never requested
        assertThat(downloader.calls).isEmpty()
        assertThat(cache.acquired).isEmpty()

        clear(vm)
        assertThat(cache.released).isEmpty()
    }

    @Test
    fun `metadata failure fails fast and retry recovers`() = runTest {
        dataSource.modelResult = Result.Error(DataError.Network.NO_INTERNET)
        val vm = viewModel()
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_no_internet)
        assertThat(downloader.calls).hasSize(0)

        dataSource.modelResult = Result.Success(model(uid))
        vm.onAction(ViewerAction.OnRetry)

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)
        assertThat(vm.state.value.error).isNull()
        assertThat(cache.acquired).containsExactly(uid)
        assertThat(cache.leases[uid]).isEqualTo(1)
    }

    @Test
    fun `download failure is surfaced with its typed message`() = runTest {
        downloader.result = Result.Error(DataError.Network.UNAUTHORIZED)
        val vm = viewModel()

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_unauthorized)
    }

    @Test
    fun `OnModelLoaded only applies while loading into the scene`() = runTest {
        downloader.result = Result.Error(DataError.Local.NO_GLB_ARCHIVE)
        val vm = viewModel()
        vm.onAction(ViewerAction.OnModelLoaded)
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
    }

    @Test
    fun `an unreadable file reported by the renderer fails with the missing-file message`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)

        vm.onAction(ViewerAction.OnModelLoadFailed(ModelLoadError.FileUnreadable))

        val state = vm.state.value
        assertThat(state.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(state)).isEqualTo(R.string.error_file_not_found)
        assertThat(state.modelFile).isNull()
        assertThat(cache.evicted).isEmpty()
    }

    @Test
    fun `a parse failure evicts the file and retry downloads it again`() = runTest {
        val vm = viewModel()
        assertThat(downloader.calls).containsExactly(uid)

        vm.onAction(ViewerAction.OnModelLoadFailed(ModelLoadError.ParseFailed))

        val state = vm.state.value
        assertThat(state.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(state)).isEqualTo(R.string.error_corrupt_file)
        assertThat(cache.evicted).containsExactly(uid)
        assertThat(cache.fileFor(uid).exists()).isFalse()

        vm.onAction(ViewerAction.OnRetry)

        assertThat(downloader.calls).containsExactly(uid, uid)
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)
        assertThat(vm.state.value.modelFile).isEqualTo(cache.fileFor(uid))
        assertThat(cache.leases[uid]).isEqualTo(1)
    }

    @Test
    fun `a renderer timeout keeps the file so Retry reloads it from the cache`() = runTest {
        val vm = viewModel()

        vm.onAction(ViewerAction.OnModelLoadFailed(ModelLoadError.Timeout))

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_render_timeout)
        assertThat(cache.evicted).isEmpty()

        vm.onAction(ViewerAction.OnRetry)
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)
        assertThat(downloader.calls).containsExactly(uid)
    }

    @Test
    fun `a reload failure after the model was Ready still surfaces the error`() = runTest {
        // A recreated view (rotation) reloads the retained file; a purged or damaged file must not be silent.
        val vm = viewModel()
        vm.onAction(ViewerAction.OnModelLoaded)
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Ready)

        vm.onAction(ViewerAction.OnModelLoadFailed(ModelLoadError.ParseFailed))

        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)
        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_corrupt_file)
        assertThat(vm.state.value.modelFile).isNull()
        assertThat(cache.evicted).containsExactly(uid)
    }

    @Test
    fun `OnModelLoadFailed is ignored once the viewer has already failed`() = runTest {
        downloader.result = Result.Error(DataError.Local.NO_GLB_ARCHIVE)
        val vm = viewModel()
        assertThat(vm.state.value.phase).isEqualTo(ViewerPhase.Failed)

        vm.onAction(ViewerAction.OnModelLoadFailed(ModelLoadError.FileUnreadable))

        assertThat(errorRes(vm.state.value)).isEqualTo(R.string.error_no_glb_archive)
        assertThat(cache.evicted).isEmpty()
    }

    @Test
    fun `back and attribution actions emit navigation events`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.onAction(ViewerAction.OnBackClick)
            assertThat(awaitItem()).isEqualTo(ViewerEvent.NavigateBack)

            vm.onAction(ViewerAction.OnOpenSketchfabPage)
            assertThat(awaitItem()).isInstanceOf(ViewerEvent.OpenUrl::class)
        }
    }

    @Test
    fun `closing the viewer releases its lease and removes the temporary file`() = runTest {
        val cleanupScope = TestScope(testDispatcher)
        val vm = viewModel(cleanupScope)
        assertThat(cache.fileFor(uid).exists()).isTrue()
        assertThat(cache.acquired).containsExactly(uid)

        clear(vm)

        assertThat(cache.released).containsExactly(uid)
        assertThat(cache.leases[uid]).isNull()
        assertThat(cache.fileFor(uid).exists()).isFalse()
    }

    @Test
    fun `a file stays until the last viewer using it closes`() = runTest {
        val first = viewModel()
        val second = viewModel() // cache hit on the file the first one downloaded
        assertThat(downloader.calls).containsExactly(uid)
        assertThat(cache.leases[uid]).isEqualTo(2)

        clear(first)
        assertThat(cache.leases[uid]).isEqualTo(1)
        assertThat(cache.fileFor(uid).exists()).isTrue()
        assertThat(second.state.value.phase).isEqualTo(ViewerPhase.LoadingIntoScene)

        clear(second)
        assertThat(cache.leases[uid]).isNull()
        assertThat(cache.fileFor(uid).exists()).isFalse()
    }

    @Test
    fun `a viewer that never got past the budget gate still balances its lease`() = runTest {
        dataSource.modelResult = Result.Success(model(uid, faceCount = 40_000))
        val vm = viewModel()
        assertThat(cache.leases[uid]).isEqualTo(1)

        clear(vm)

        assertThat(cache.released).containsExactly(uid)
        assertThat(cache.leases).isEmpty()
    }
}
