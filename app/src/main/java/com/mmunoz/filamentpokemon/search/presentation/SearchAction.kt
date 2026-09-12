package com.mmunoz.filamentpokemon.search.presentation

sealed interface SearchAction {
    data class OnQueryChange(val query: String) : SearchAction
    data object OnSearchSubmit : SearchAction
    data object OnClearQuery : SearchAction
    data object OnLoadMore : SearchAction
    data object OnRetry : SearchAction
    data class OnModelClick(val uid: String) : SearchAction
}
