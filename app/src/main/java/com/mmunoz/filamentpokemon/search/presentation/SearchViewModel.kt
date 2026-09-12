package com.mmunoz.filamentpokemon.search.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmunoz.filamentpokemon.core.domain.util.onFailure
import com.mmunoz.filamentpokemon.core.domain.util.onSuccess
import com.mmunoz.filamentpokemon.core.presentation.util.toUiText
import com.mmunoz.filamentpokemon.search.domain.PolygonBudget
import com.mmunoz.filamentpokemon.search.domain.SketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.presentation.mappers.toUi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class SearchViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val modelDataSource: SketchfabModelDataSource
) : ViewModel() {

    private val _state = MutableStateFlow(
        SearchState(query = savedStateHandle[KEY_QUERY] ?: "")
    )
    val state = _state.asStateFlow()

    private val _events = Channel<SearchEvent>()
    val events = _events.receiveAsFlow()

    /** Typed query, debounced before it hits the network. */
    private val queryInput = MutableStateFlow(_state.value.query)

    /** Face budget the search is filtered by (Step 5 feeds this from DataStore). */
    private val maxFaceCount = MutableStateFlow(PolygonBudget.DEFAULT_MAX_FACES)

    private var searchJob: Job? = null

    init {
        combine(
            queryInput.debounce(QUERY_DEBOUNCE_MS),
            maxFaceCount
        ) { query, maxFaces -> SearchParams(query.trim(), maxFaces) }
            .distinctUntilChanged()
            .onEach { startNewSearch(it) }
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

            // Bypass the debounce: search immediately with the current input.
            SearchAction.OnSearchSubmit,
            SearchAction.OnRetry -> startNewSearch(
                SearchParams(_state.value.query.trim(), maxFaceCount.value)
            )

            SearchAction.OnLoadMore -> loadNextPage()

            is SearchAction.OnModelClick -> {
                val model = _state.value.models.firstOrNull { it.uid == action.uid } ?: return
                viewModelScope.launch {
                    _events.send(SearchEvent.NavigateToViewer(model.uid, model.name))
                }
            }
        }
    }

    private fun startNewSearch(params: SearchParams) {
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
                            models = page.models.map { model -> model.toUi(params.maxFaceCount) },
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
                        it.copy(
                            isLoadingMore = false,
                            models = it.models + page.models.map { model -> model.toUi(it.maxFaceCount) },
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
