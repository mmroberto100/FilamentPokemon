package com.mmunoz.filamentpokemon.viewer.presentation

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mmunoz.filamentpokemon.viewer.filament.FilamentModelRenderer
import java.io.File

/**
 * Hosts a Filament [SurfaceView]. The renderer is created with the view, runs only while the
 * lifecycle is RESUMED, loads [modelFile] whenever it changes, and is torn down when the view
 * leaves the window (see [FilamentModelRenderer] for the destroy contract).
 */
@Composable
fun FilamentView(
    modelFile: File?,
    modifier: Modifier = Modifier,
    onModelLoaded: () -> Unit = {}
) {
    var renderer by remember { mutableStateOf<FilamentModelRenderer?>(null) }
    val currentOnModelLoaded by rememberUpdatedState(onModelLoaded)
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { context ->
            SurfaceView(context).also { view ->
                renderer = FilamentModelRenderer(view).also { created ->
                    created.onModelLoaded = { currentOnModelLoaded() }
                }
            }
        },
        modifier = modifier
    )

    DisposableEffect(lifecycleOwner, renderer) {
        val r = renderer ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> r.start()
                Lifecycle.Event.ON_PAUSE -> r.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) r.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            r.stop()
        }
    }

    LaunchedEffect(modelFile, renderer) {
        val r = renderer ?: return@LaunchedEffect
        val file = modelFile ?: return@LaunchedEffect
        r.loadGlb(file)
    }
}
