package com.mmunoz.filamentpokemon.viewer.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.presentation.util.ObserveAsEvents
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import com.mmunoz.filamentpokemon.ui.theme.FilamentPokemonTheme
import org.koin.androidx.compose.koinViewModel

private val log = Logger.withTag("ViewerScreen")

@Composable
fun ViewerRoot(
    onNavigateBack: () -> Unit,
    viewModel: ViewerViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ViewerEvent.NavigateBack -> onNavigateBack()
            is ViewerEvent.OpenUrl -> try {
                context.startActivity(Intent(Intent.ACTION_VIEW, event.url.toUri()))
            } catch (e: ActivityNotFoundException) {
                log.w(e) { "No activity can open ${event.url}" }
            }
        }
    }

    ViewerScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun ViewerScreen(
    state: ViewerState,
    onAction: (ViewerAction) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (state.phase != ViewerPhase.Failed) {
            FilamentView(
                modelFile = state.modelFile,
                onModelLoaded = { onAction(ViewerAction.OnModelLoaded) },
                onModelLoadFailed = { onAction(ViewerAction.OnModelLoadFailed(it)) },
                modifier = Modifier.fillMaxSize()
            )
        }

        TopBar(
            title = state.name,
            onBack = { onAction(ViewerAction.OnBackClick) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        when (state.phase) {
            ViewerPhase.CheckingBudget -> ProgressOverlay(
                label = stringResource(R.string.viewer_checking_budget),
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerPhase.Downloading -> ProgressOverlay(
                label = state.downloadProgress?.let { stringResource(R.string.viewer_downloading, (it * 100).toInt()) }
                    ?: stringResource(R.string.viewer_downloading_indeterminate),
                progress = state.downloadProgress,
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerPhase.LoadingIntoScene -> ProgressOverlay(
                label = stringResource(R.string.viewer_loading_scene),
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerPhase.Failed -> ErrorOverlay(
                message = state.error,
                onRetry = { onAction(ViewerAction.OnRetry) },
                onBack = { onAction(ViewerAction.OnBackClick) },
                modifier = Modifier.align(Alignment.Center)
            )

            ViewerPhase.Ready -> Unit
        }

        state.info?.let { info ->
            AttributionCard(
                info = info,
                onOpenSketchfab = { onAction(ViewerAction.OnOpenSketchfabPage) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(end = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_navigate_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProgressOverlay(label: String, modifier: Modifier = Modifier, progress: Float? = null) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                CircularProgressIndicator(progress = { progress })
            }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorOverlay(
    message: UiText?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(32.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = message?.asString() ?: stringResource(R.string.error_unknown),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack) { Text(stringResource(R.string.viewer_back)) }
                Button(onClick = onRetry) { Text(stringResource(R.string.viewer_retry)) }
            }
        }
    }
}

/** CC licences require attribution – always visible while a model is on screen. */
@Composable
private fun AttributionCard(
    info: ModelInfoUi,
    onOpenSketchfab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.viewer_by_author, info.author),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(
                            info.licenseLabel,
                            stringResource(R.string.viewer_faces, info.formattedFaceCount)
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (info.isAnimated) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = stringResource(R.string.cd_animated_model),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            TextButton(onClick = onOpenSketchfab) { Text(stringResource(R.string.viewer_open_sketchfab)) }
        }
    }
}

private val previewInfo = ModelInfoUi(
    author = "Wesai",
    licenseLabel = "CC Attribution",
    formattedFaceCount = "10,041",
    viewerUrl = "https://sketchfab.com/3d-models/x",
    isAnimated = true
)

@Preview(showBackground = true)
@Composable
private fun ViewerScreenDownloadingPreview() {
    FilamentPokemonTheme {
        ViewerScreen(
            state = ViewerState(uid = "x", name = "Pokemon Center", phase = ViewerPhase.Downloading, downloadProgress = 0.42f, info = previewInfo),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ViewerScreenDownloadingIndeterminatePreview() {
    FilamentPokemonTheme {
        ViewerScreen(
            state = ViewerState(uid = "x", name = "Pokemon Center", phase = ViewerPhase.Downloading, downloadProgress = null, info = previewInfo),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ViewerScreenFailedPreview() {
    FilamentPokemonTheme {
        ViewerScreen(
            state = ViewerState(uid = "x", name = "Pokemon Center", phase = ViewerPhase.Failed, error = UiText.StringResource(R.string.error_over_polygon_budget)),
            onAction = {}
        )
    }
}
