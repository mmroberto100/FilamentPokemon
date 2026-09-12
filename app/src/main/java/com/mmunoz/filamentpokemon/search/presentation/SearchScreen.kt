package com.mmunoz.filamentpokemon.search.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.presentation.util.ObserveAsEvents
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import com.mmunoz.filamentpokemon.search.presentation.components.ModelCard
import com.mmunoz.filamentpokemon.ui.theme.FilamentPokemonTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.text.NumberFormat

@Composable
fun SearchRoot(
    onNavigateToViewer: (uid: String, name: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SearchEvent.NavigateToViewer -> onNavigateToViewer(event.uid, event.name)
            is SearchEvent.ShowError -> {
                val message = event.message.asString(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    SearchScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.search_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onAction(SearchAction.OnQueryChange(it)) },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search))
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onAction(SearchAction.OnClearQuery) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_query))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onAction(SearchAction.OnSearchSubmit)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Text(
                text = stringResource(
                    R.string.search_budget_label,
                    NumberFormat.getIntegerInstance().format(state.maxFaceCount)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            when {
                state.isLoading && state.models.isEmpty() -> CenteredBox { CircularProgressIndicator() }

                state.error != null && state.models.isEmpty() -> ErrorState(
                    message = state.error,
                    onRetry = { onAction(SearchAction.OnRetry) }
                )

                state.models.isEmpty() -> CenteredBox {
                    Text(
                        text = stringResource(R.string.search_empty),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }

                else -> ModelGrid(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ModelGrid(
    state: SearchState,
    onAction: (SearchAction) -> Unit
) {
    val gridState = rememberLazyGridState()
    LoadMoreOnScrollEnd(gridState = gridState, itemCount = state.models.size, enabled = state.canLoadMore) {
        onAction(SearchAction.OnLoadMore)
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = state.models, key = { it.uid }) { model ->
            ModelCard(
                model = model,
                onClick = { onAction(SearchAction.OnModelClick(model.uid)) }
            )
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) { CircularProgressIndicator() }
            }
        }
    }
}

/** Fires [onLoadMore] when the grid scrolls within [threshold] items of its end. */
@Composable
private fun LoadMoreOnScrollEnd(
    gridState: LazyGridState,
    itemCount: Int,
    enabled: Boolean,
    threshold: Int = 4,
    onLoadMore: () -> Unit
) {
    val shouldLoadMore by remember(itemCount, enabled) {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            enabled && itemCount > 0 && lastVisible >= itemCount - 1 - threshold
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
}

@Composable
private fun ErrorState(message: UiText, onRetry: () -> Unit) {
    CenteredBox {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text(text = message.asString(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.search_retry)) }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) { content() }
}

private val previewModels = listOf(
    PokemonModelUi("1", "Pikachu", "Wesai", null, 4_500, "4,500", 0.15f, isAnimated = true, "CC Attribution"),
    PokemonModelUi("2", "Charizard – Low Poly", "j-orgaz", null, 21_300, "21,300", 0.71f, isAnimated = false, "CC Attribution"),
    PokemonModelUi("3", "Pokemon Center RSE", "Wesai", null, 28_900, "28,900", 0.96f, isAnimated = false, "CC Attribution-NC"),
    PokemonModelUi("4", "Eevee", "trainer", null, 9_800, "9,800", 0.33f, isAnimated = true, null)
)

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    FilamentPokemonTheme {
        SearchScreen(state = SearchState(query = "pika", models = previewModels), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenErrorPreview() {
    FilamentPokemonTheme {
        SearchScreen(
            state = SearchState(error = UiText.StringResource(R.string.error_no_internet)),
            onAction = {}
        )
    }
}
