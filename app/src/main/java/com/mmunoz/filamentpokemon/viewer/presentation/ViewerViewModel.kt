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
import com.mmunoz.filamentpokemon.viewer.domain.ModelCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat

/**
 * Drives the CLAUDE.md pipeline for one model:
 * metadata → triangle-budget gate → cache hit or download → render → evict on close.
 *
 * @param cleanupScope outlives [viewModelScope] (which is already cancelled in [onCleared]) so the
 *                     cache eviction on close always completes.
 */
class ViewerViewModel(
    savedStateHandle: SavedStateHandle,
    private val modelDataSource: SketchfabModelDataSource,
    private val downloader: GlbDownloader,
    private val cache: ModelCache,
    private val userPreferences: UserPreferences,
    private val cleanupScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ViewModel() {

    private val uid: String = checkNotNull(savedStateHandle[ARG_UID]) { "ViewerRoute.uid missing" }

    private val _state = MutableStateFlow(
        ViewerState(uid = uid, name = savedStateHandle[ARG_NAME] ?: "")
    )
    val state = _state.asStateFlow()

    private val _events = Channel<ViewerEvent>()
    val events = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: ViewerAction) {
        when (action) {
            ViewerAction.OnRetry -> load()
            ViewerAction.OnModelLoaded -> _state.update {
                if (it.phase == ViewerPhase.LoadingIntoScene) it.copy(phase = ViewerPhase.Ready) else it
            }
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
                it.copy(phase = ViewerPhase.CheckingBudget, error = null, downloadProgress = 0f, modelFile = null)
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
                val downloaded = downloader.download(uid, cache.fileFor(uid)) { progress ->
                    _state.update { it.copy(downloadProgress = progress.fraction) }
                }
                when (downloaded) {
                    is Result.Error -> return@launch fail(downloaded.error)
                    is Result.Success -> downloaded.data
                }
            }
            cache.enforceLimit()

            // 4. Hand the file to the renderer; OnModelLoaded flips the phase to Ready.
            _state.update { it.copy(phase = ViewerPhase.LoadingIntoScene, downloadProgress = 1f, modelFile = file) }
        }
    }

    private fun fail(error: DataError) {
        _state.update { it.copy(phase = ViewerPhase.Failed, error = error.toUiText(), modelFile = null) }
    }

    /** CLAUDE.md §3: the temporary file is removed as soon as the viewer is closed. */
    override fun onCleared() {
        cleanupScope.launch { cache.evict(uid) }
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
