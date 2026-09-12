package com.mmunoz.filamentpokemon.viewer.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmunoz.filamentpokemon.core.domain.model.PokemonModel
import com.mmunoz.filamentpokemon.core.domain.model.fitsBudget
import com.mmunoz.filamentpokemon.core.domain.preferences.UserPreferences
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.core.presentation.util.toUiText
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
import com.mmunoz.filamentpokemon.viewer.domain.GlbDownloader
import com.mmunoz.filamentpokemon.viewer.domain.GlbHeader
import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import com.mmunoz.filamentpokemon.viewer.filament.ModelLoadError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/**
 * Drives the CLAUDE.md pipeline for one model:
 * metadata → triangle-budget gate → cache hit or download → header check → render → release on close.
 *
 * This instance holds one cache lease on its uid from the first [load] until [onCleared]; the
 * file is deleted when the last viewer using it releases (CLAUDE.md §3), never while another
 * viewer for the same uid is still rendering or downloading it.
 *
 * @param cleanupScope outlives [viewModelScope] (which is already cancelled in [onCleared]) so the
 *                     lease release on close always completes.
 */
class ViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val modelDataSource: SketchfabModelDataSource,
    private val downloader: GlbDownloader,
    private val cache: ModelCache,
    private val userPreferences: UserPreferences,
    private val cleanupScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val uid: String = checkNotNull(savedStateHandle[ARG_UID]) { "ViewerRoute.uid missing" }

    private val _state = MutableStateFlow(
        ViewerState(uid = uid, name = savedStateHandle[ARG_NAME] ?: "")
    )
    val state = _state.asStateFlow()

    private val _events = Channel<ViewerEvent>()
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var evictJob: Job? = null
    private var leaseHeld = false

    init {
        load()
    }

    fun onAction(action: ViewerAction) {
        when (action) {
            ViewerAction.OnRetry -> load()
            ViewerAction.OnModelLoaded -> _state.update {
                if (it.phase == ViewerPhase.LoadingIntoScene) it.copy(phase = ViewerPhase.Ready) else it
            }
            is ViewerAction.OnModelLoadFailed -> onModelLoadFailed(action.reason)
            ViewerAction.OnBackClick -> viewModelScope.launch { _events.send(ViewerEvent.NavigateBack) }
            ViewerAction.OnOpenSketchfabPage -> {
                val url = _state.value.info?.viewerUrl ?: return
                viewModelScope.launch { _events.send(ViewerEvent.OpenUrl(url)) }
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(phase = ViewerPhase.CheckingBudget, error = null, downloadProgress = null, modelFile = null)
            }
            // A retry after a corrupt file must not find the bytes being thrown away.
            evictJob?.join()

            // 0. A uid that cannot name a cache file reaches neither the network nor the disk.
            val destination = try {
                cache.fileFor(uid)
            } catch (e: IllegalArgumentException) {
                return@launch fail(DataError.Local.NOT_FOUND)
            }
            // Leased before any lookup so a concurrent release cannot remove the file or an in-flight .part.
            if (!leaseHeld) {
                cache.acquire(uid)
                leaseHeld = true
            }

            // 1. Fresh metadata (also feeds the attribution card).
            val model = when (val result = modelDataSource.getModel(uid)) {
                is Result.Error -> return@launch fail(result.error)
                is Result.Success -> result.data
            }
            _state.update { it.copy(name = model.name.ifBlank { it.name }, info = model.toInfoUi()) }

            // 2. Hard gate: never start a download above the user's triangle budget.
            val maxFaceCount = userPreferences.maxFaceCount.first()
            if (!model.fitsBudget(maxFaceCount)) return@launch fail(DataError.Local.OVER_POLYGON_BUDGET)
            if (!model.isDownloadable) return@launch fail(DataError.Network.FORBIDDEN)

            // 3. Reuse a file that is already in the cache dir, otherwise stream it in.
            val file = cache.get(uid) ?: run {
                _state.update { it.copy(phase = ViewerPhase.Downloading) }
                val downloaded = downloader.download(uid, destination) { progress ->
                    _state.update { it.copy(downloadProgress = progress.fractionOrNull) }
                }
                when (downloaded) {
                    is Result.Error -> return@launch fail(downloaded.error)
                    is Result.Success -> downloaded.data
                }
            }
            cache.enforceLimit()

            // 4. Only a well-formed container reaches native code, and only while it still exists.
            if (!withContext(ioDispatcher) { GlbHeader.isValid(file) }) {
                cache.evict(uid)
                return@launch fail(DataError.Local.CORRUPT_FILE)
            }
            if (cache.get(uid) == null) return@launch fail(DataError.Local.NOT_FOUND)

            // 5. Hand the file to the renderer; OnModelLoaded flips the phase to Ready.
            _state.update { it.copy(phase = ViewerPhase.LoadingIntoScene, downloadProgress = 1f, modelFile = file) }
        }
    }

    private fun onModelLoadFailed(reason: ModelLoadError) {
        if (_state.value.phase != ViewerPhase.LoadingIntoScene) return
        when (reason) {
            ModelLoadError.FileUnreadable -> fail(DataError.Local.NOT_FOUND)
            ModelLoadError.ParseFailed,
            ModelLoadError.Timeout -> {
                fail(DataError.Local.CORRUPT_FILE)
                // Retry must download fresh bytes instead of re-loading the same file.
                evictJob = viewModelScope.launch { cache.evict(uid) }
            }
        }
    }

    private fun fail(error: DataError) {
        _state.update { it.copy(phase = ViewerPhase.Failed, error = error.toUiText(), modelFile = null) }
    }

    /** CLAUDE.md §3: dropping the lease removes the temporary file once no other viewer uses it. */
    override fun onCleared() {
        if (leaseHeld) cleanupScope.launch { cache.release(uid) }
    }

    private fun PokemonModel.toInfoUi() = ModelInfoUi(
        author = author,
        licenseLabel = licenseLabel,
        formattedFaceCount = NumberFormat.getIntegerInstance().format(faceCount),
        viewerUrl = viewerUrl,
        isAnimated = isAnimated
    )

    companion object {
        /** Route argument names – navigation stores `ViewerRoute` fields under these keys. */
        const val ARG_UID = "uid"
        const val ARG_NAME = "name"
    }
}
