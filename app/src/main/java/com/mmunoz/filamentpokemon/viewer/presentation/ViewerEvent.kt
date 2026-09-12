package com.mmunoz.filamentpokemon.viewer.presentation

sealed interface ViewerEvent {
    data object NavigateBack : ViewerEvent
    data class OpenUrl(val url: String) : ViewerEvent
}
