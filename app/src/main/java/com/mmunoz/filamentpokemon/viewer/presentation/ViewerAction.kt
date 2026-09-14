package com.mmunoz.filamentpokemon.viewer.presentation

import com.mmunoz.filamentpokemon.viewer.filament.ModelLoadError

sealed interface ViewerAction {
    data object OnRetry : ViewerAction
    data object OnBackClick : ViewerAction
    /** The renderer finished uploading the model's resources. */
    data object OnModelLoaded : ViewerAction
    /** The renderer gave up on the model file. */
    data class OnModelLoadFailed(val reason: ModelLoadError) : ViewerAction
    data object OnOpenSketchfabPage : ViewerAction
}
