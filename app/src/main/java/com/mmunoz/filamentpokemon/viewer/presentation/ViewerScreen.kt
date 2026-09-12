package com.mmunoz.filamentpokemon.viewer.presentation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmunoz.filamentpokemon.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Placeholder destination. Step 8 replaces this with ViewerRoot/ViewerScreen driven by a
 * ViewModel (download → cache → render). For Step 7 it renders the debug sample model so the
 * Filament pipeline can be verified on a device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    uid: String,
    name: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var sampleFile by remember { mutableStateOf<File?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // TEMPORARY (Step 7 smoke test): copy the debug-only sample asset into the cache dir.
    LaunchedEffect(Unit) { sampleFile = context.copyDebugSample() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (loaded) "$name · sample ready" else name) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FilamentView(
                modelFile = sampleFile,
                onModelLoaded = { loaded = true },
                modifier = Modifier.fillMaxSize()
            )
            Text(
                text = uid,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }
    }
}

private suspend fun Context.copyDebugSample(): File? = withContext(Dispatchers.IO) {
    runCatching {
        val target = File(cacheDir, "models/debug-sample.glb").apply { parentFile?.mkdirs() }
        assets.open("models/sample.glb").use { input -> target.outputStream().use { input.copyTo(it) } }
        target
    }.getOrNull()
}
