package com.mmunoz.filamentpokemon.viewer.presentation

import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import java.io.File

data class ViewerState(
    val uid: String,
    val name: String,
    val phase: ViewerPhase = ViewerPhase.CheckingBudget,
    /** 0f..1f while [ViewerPhase.Downloading]. */
    val downloadProgress: Float = 0f,
    /** Local `.glb` inside the cache dir once it is ready to be rendered. */
    val modelFile: File? = null,
    /** Fresh metadata for the attribution card; null until fetched. */
    val info: ModelInfoUi? = null,
    val error: UiText? = null
)

enum class ViewerPhase {
    CheckingBudget,
    Downloading,
    LoadingIntoScene,
    Ready,
    Failed
}

data class ModelInfoUi(
    val author: String,
    val licenseLabel: String?,
    /** Locale-formatted, e.g. "10,041". */
    val formattedFaceCount: String,
    val viewerUrl: String,
    val isAnimated: Boolean
)
