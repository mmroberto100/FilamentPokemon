package com.mmunoz.filamentpokemon.search.presentation

import com.mmunoz.filamentpokemon.core.presentation.util.UiText

sealed interface SearchEvent {
    data class NavigateToViewer(val uid: String, val name: String) : SearchEvent
    data class ShowError(val message: UiText) : SearchEvent
}
