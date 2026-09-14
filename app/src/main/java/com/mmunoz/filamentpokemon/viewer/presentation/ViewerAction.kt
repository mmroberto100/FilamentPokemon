package com.mmunoz.filamentpokemon.viewer.presentation

sealed interface ViewerAction {
    data object OnRetry : ViewerAction
    data object OnBackClick : ViewerAction
    /** The renderer finished uploading the model's resources. */
    data object OnModelLoaded : ViewerAction
    data object OnOpenSketchfabPage : ViewerAction
}
