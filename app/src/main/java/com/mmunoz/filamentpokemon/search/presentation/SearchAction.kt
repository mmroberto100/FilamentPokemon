package com.mmunoz.filamentpokemon.search.presentation

sealed interface SearchAction {
    data class OnQueryChange(val query: String) : SearchAction
    data object OnSearchSubmit : SearchAction
    data object OnClearQuery : SearchAction
    data object OnLoadMore : SearchAction
    data object OnRetry : SearchAction
    data class OnModelClick(val uid: String) : SearchAction

    data object OnOpenBudgetSheet : SearchAction
    data object OnDismissBudgetSheet : SearchAction
    /** Live slider value while dragging – updates the label only. */
    data class OnMaxFaceCountChange(val value: Int) : SearchAction
    /** Drag finished – persist the budget; the new value re-runs the search. */
    data object OnMaxFaceCountChangeFinished : SearchAction
}
