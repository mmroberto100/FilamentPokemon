package com.mmunoz.filamentpokemon.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmunoz.filamentpokemon.core.domain.util.onFailure
import com.mmunoz.filamentpokemon.core.domain.util.onSuccess
import com.mmunoz.filamentpokemon.core.presentation.util.toUiText
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.preferences.UserPreferences
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.presentation.mappers.toUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val modelDataSource: SketchfabModelDataSource,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(
        SearchState(query = savedStateHandle[KEY_QUERY] ?: "")
    )
    val state = _state.asStateFlow()

    private val _events = Channel<SearchEvent>()
    val events = _events.receiveAsFlow()

    /** Typed query, debounced before it hits the network. */
    private val queryInput = MutableStateFlow(_state.value.query)

    /** Persisted face budget; every emission (initial or user change) re-runs the search. */
    private val maxFaceCount = userPreferences.maxFaceCount
        .onEach { budget -> _state.update { it.copy(maxFaceCount = budget) } }

    private var searchJob: Job? = null

    /** Params of the running or last finished first-page search. */
    private var lastSearchParams: SearchParams? = null

    init {
        combine(
            queryInput.debounce(QUERY_DEBOUNCE_MS),
            maxFaceCount
        ) { query, maxFaces -> SearchParams(query.trim(), maxFaces) }
            .onEach { search(it, force = false) }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.OnQueryChange -> {
                savedStateHandle[KEY_QUERY] = action.query
                _state.update { it.copy(query = action.query) }
                queryInput.value = action.query
            }

            SearchAction.OnClearQuery -> onAction(SearchAction.OnQueryChange(""))

            // Bypass the debounce: search immediately with the current input, even if unchanged.
            SearchAction.OnSearchSubmit,
            SearchAction.OnRetry -> search(
                SearchParams(_state.value.query.trim(), _state.value.maxFaceCount),
                force = true
            )

            SearchAction.OnLoadMore -> loadNextPage()

            is SearchAction.OnModelClick -> {
                val model = _state.value.models.firstOrNull { it.uid == action.uid } ?: return
                viewModelScope.launch {
                    _events.send(SearchEvent.NavigateToViewer(model.uid, model.name))
                }
            }

            SearchAction.OnOpenBudgetSheet -> _state.update { it.copy(isBudgetSheetVisible = true) }
            SearchAction.OnDismissBudgetSheet -> _state.update { it.copy(isBudgetSheetVisible = false) }

            is SearchAction.OnMaxFaceCountChange -> _state.update {
                it.copy(maxFaceCount = PolygonBudget.clamp(action.value))
            }

            SearchAction.OnMaxFaceCountChangeFinished -> viewModelScope.launch {
                userPreferences.setMaxFaceCount(_state.value.maxFaceCount)
                    .onFailure { error ->
                        // The label must not advertise a budget the search never adopted.
                        _state.update { it.copy(maxFaceCount = userPreferences.maxFaceCount.first()) }
                        _events.send(SearchEvent.ShowError(error.toUiText()))
                    }
            }
        }
    }

    /**
     * Single entry point for a first-page search. The debounced pipeline also re-emits the params
     * of a search that Submit/Retry already started; that repeat must not cancel or reset it.
     */
    private fun search(params: SearchParams, force: Boolean) {
        if (!force && params == lastSearchParams) return
        lastSearchParams = params

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    error = null,
                    models = emptyList(),
                    nextCursor = null,
                    endReached = false,
                    maxFaceCount = params.maxFaceCount
                )
            }
            modelDataSource.search(query = params.query, maxFaceCount = params.maxFaceCount)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            models = page.models
                                .map { model -> model.toUi(params.maxFaceCount) }
                                .distinctBy(PokemonModelUi::uid),
                            nextCursor = page.nextCursor,
                            endReached = page.nextCursor == null
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.toUiText()) }
                }
        }
    }

    private fun loadNextPage() {
        val current = _state.value
        if (!current.canLoadMore) return
        val cursor = current.nextCursor ?: return

        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            modelDataSource.search(
                query = current.query.trim(),
                maxFaceCount = current.maxFaceCount,
                cursor = cursor
            )
                .onSuccess { page ->
                    _state.update {
                        // Cursor pagination over a live sort can repeat a model across pages;
                        // the grid keys items by uid, so keep the first occurrence only.
                        val incoming = page.models.map { model -> model.toUi(it.maxFaceCount) }
                        it.copy(
                            isLoadingMore = false,
                            models = (it.models + incoming).distinctBy(PokemonModelUi::uid),
                            nextCursor = page.nextCursor,
                            endReached = page.nextCursor == null
                        )
                    }
                }
                .onFailure { error ->
                    // Keep what we already have; surface the failure as a transient message.
                    _state.update { it.copy(isLoadingMore = false) }
                    _events.send(SearchEvent.ShowError(error.toUiText()))
                }
        }
    }

    private data class SearchParams(val query: String, val maxFaceCount: Int)

    private companion object {
        const val KEY_QUERY = "query"
        const val QUERY_DEBOUNCE_MS = 400L
    }
}
