package com.mmunoz.filamentpokemon.search.presentation

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mmunoz.filamentpokemon.core.domain.util.DataError
import com.mmunoz.filamentpokemon.core.domain.util.Result
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import com.mmunoz.filamentpokemon.search.domain.FakeSketchfabModelDataSource
import com.mmunoz.filamentpokemon.search.domain.FakeSketchfabModelDataSource.Companion.model
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.domain.preferences.FakeUserPreferences
import com.mmunoz.filamentpokemon.search.domain.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var dataSource: FakeSketchfabModelDataSource
    private lateinit var userPreferences: FakeUserPreferences

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dataSource = FakeSketchfabModelDataSource()
        userPreferences = FakeUserPreferences()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        SearchViewModel(savedState, dataSource, userPreferences)

    private fun page(vararg uids: String, next: String? = null) =
        Result.Success(SearchPage(uids.map { model(it) }, next))

    @Test
    fun `initial search runs after debounce with plain pokemon query and default budget`() = runTest {
        dataSource.pageQueue += page("a", "b", next = "24")
        val vm = viewModel()

        assertThat(dataSource.searchCalls).hasSize(0)
        advanceTimeBy(401)

        assertThat(dataSource.searchCalls).containsExactly(
            FakeSketchfabModelDataSource.SearchCall("", PolygonBudget.DEFAULT_MAX_FACES, null)
        )
        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.models.map { it.uid }).containsExactly("a", "b")
        assertThat(state.nextCursor).isEqualTo("24")
        assertThat(state.endReached).isFalse()
        assertThat(state.models.first().formattedFaceCount).isEqualTo("10,000")
    }

    @Test
    fun `typing is debounced into a single search with the final query`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401) // initial search
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnQueryChange("p"))
        advanceTimeBy(100)
        vm.onAction(SearchAction.OnQueryChange("pi"))
        advanceTimeBy(100)
        vm.onAction(SearchAction.OnQueryChange("pika"))
        assertThat(dataSource.searchCalls).hasSize(0)

        advanceTimeBy(401)
        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("pika")
        assertThat(vm.state.value.query).isEqualTo("pika")
    }

    @Test
    fun `submit bypasses the debounce`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnQueryChange("eevee"))
        vm.onAction(SearchAction.OnSearchSubmit)

        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("eevee")
    }

    @Test
    fun `submit inside the debounce window issues one request and keeps its results`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()
        dataSource.pageQueue += page("a")

        vm.onAction(SearchAction.OnQueryChange("eevee"))
        vm.onAction(SearchAction.OnSearchSubmit)
        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a")

        // The debounce now emits the same params; that must not restart the search.
        advanceTimeBy(401)

        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("eevee")
        assertThat(vm.state.value.isLoading).isFalse()
        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a")
    }

    @Test
    fun `submitting the same query again is a refresh and re-requests`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnQueryChange("eevee"))
        vm.onAction(SearchAction.OnSearchSubmit)
        vm.onAction(SearchAction.OnSearchSubmit)

        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("eevee", "eevee")
    }

    @Test
    fun `typing a new query after a submit is still debounced into one request`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnQueryChange("eevee"))
        vm.onAction(SearchAction.OnSearchSubmit)
        vm.onAction(SearchAction.OnQueryChange("eevee "))
        advanceTimeBy(100)
        vm.onAction(SearchAction.OnQueryChange("eevee s"))
        assertThat(dataSource.searchCalls).hasSize(1)

        advanceTimeBy(401)
        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("eevee", "eevee s")
    }

    @Test
    fun `load more appends the next page and marks the end`() = runTest {
        dataSource.pageQueue += page("a", next = "1")
        dataSource.pageQueue += page("b", next = null)
        val vm = viewModel()
        advanceTimeBy(401)

        vm.onAction(SearchAction.OnLoadMore)

        assertThat(dataSource.searchCalls.last().cursor).isEqualTo("1")
        val state = vm.state.value
        assertThat(state.models.map { it.uid }).containsExactly("a", "b")
        assertThat(state.endReached).isTrue()
        assertThat(state.canLoadMore).isFalse()

        // Nothing left to load: further requests are ignored.
        vm.onAction(SearchAction.OnLoadMore)
        assertThat(dataSource.searchCalls).hasSize(2)
    }

    @Test
    fun `load more drops models already in the list and keeps the first page order`() = runTest {
        dataSource.pageQueue += page("a", "b", next = "1")
        dataSource.pageQueue += page("b", "c", "a", next = null)
        val vm = viewModel()
        advanceTimeBy(401)

        vm.onAction(SearchAction.OnLoadMore)

        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a", "b", "c")
        assertThat(vm.state.value.endReached).isTrue()
    }

    @Test
    fun `first page drops duplicate uids`() = runTest {
        dataSource.pageQueue += page("a", "b", "a")
        val vm = viewModel()
        advanceTimeBy(401)

        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a", "b")
    }

    @Test
    fun `first page failure exposes the error in state`() = runTest {
        dataSource.pageQueue += Result.Error(DataError.Network.NO_INTERNET)
        val vm = viewModel()
        advanceTimeBy(401)

        val state = vm.state.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNotNull().isInstanceOf(UiText.StringResource::class)
        assertThat(state.models).hasSize(0)
    }

    @Test
    fun `retry clears the error and searches again`() = runTest {
        dataSource.pageQueue += Result.Error(DataError.Network.SERVER_ERROR)
        dataSource.pageQueue += page("a")
        val vm = viewModel()
        advanceTimeBy(401)

        vm.onAction(SearchAction.OnRetry)

        assertThat(dataSource.searchCalls).hasSize(2)
        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a")
    }

    @Test
    fun `load more failure keeps the list and emits a ShowError event`() = runTest {
        dataSource.pageQueue += page("a", next = "1")
        dataSource.pageQueue += Result.Error(DataError.Network.TOO_MANY_REQUESTS)
        val vm = viewModel()
        advanceTimeBy(401)

        vm.events.test {
            vm.onAction(SearchAction.OnLoadMore)
            assertThat(awaitItem()).isInstanceOf(SearchEvent.ShowError::class)
        }
        assertThat(vm.state.value.models.map { it.uid }).containsExactly("a")
        assertThat(vm.state.value.isLoadingMore).isFalse()
    }

    @Test
    fun `clicking a model emits NavigateToViewer with its name`() = runTest {
        dataSource.pageQueue += Result.Success(SearchPage(listOf(model("xyz", name = "Pikachu")), null))
        val vm = viewModel()
        advanceTimeBy(401)

        vm.events.test {
            vm.onAction(SearchAction.OnModelClick("xyz"))
            assertThat(awaitItem()).isEqualTo(SearchEvent.NavigateToViewer("xyz", "Pikachu"))
        }
    }

    @Test
    fun `query is restored from SavedStateHandle and persisted on change`() = runTest {
        val savedState = SavedStateHandle(mapOf("query" to "mew"))
        val vm = viewModel(savedState)
        assertThat(vm.state.value.query).isEqualTo("mew")
        advanceTimeBy(401)
        assertThat(dataSource.searchCalls.single().query).isEqualTo("mew")

        vm.onAction(SearchAction.OnQueryChange("mewtwo"))
        assertThat(savedState.get<String>("query")).isEqualTo("mewtwo")
    }

    @Test
    fun `clear query resets input and re-searches plain pokemon`() = runTest {
        val vm = viewModel(SavedStateHandle(mapOf("query" to "ditto")))
        advanceTimeBy(401)
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnClearQuery)
        advanceTimeBy(401)

        assertThat(vm.state.value.query).isEqualTo("")
        assertThat(dataSource.searchCalls.map { it.query }).containsExactly("")
    }

    @Test
    fun `stored budget is used for the initial search and shown in state`() = runTest {
        userPreferences = FakeUserPreferences(initial = 12_000)
        val vm = viewModel()
        advanceTimeBy(401)

        assertThat(vm.state.value.maxFaceCount).isEqualTo(12_000)
        assertThat(dataSource.searchCalls.single().maxFaceCount).isEqualTo(12_000)
    }

    @Test
    fun `dragging the slider only updates the label until the drag finishes`() = runTest {
        dataSource.pageQueue += page("a")
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()

        vm.onAction(SearchAction.OnMaxFaceCountChange(10_000))
        assertThat(vm.state.value.maxFaceCount).isEqualTo(10_000)
        assertThat(dataSource.searchCalls).hasSize(0)

        vm.onAction(SearchAction.OnMaxFaceCountChangeFinished)
        advanceTimeBy(1)

        assertThat(dataSource.searchCalls.single().maxFaceCount).isEqualTo(10_000)
        assertThat(userPreferences.maxFaceCount.first()).isEqualTo(10_000)
    }

    @Test
    fun `failed budget write shows an error and restores the stored budget`() = runTest {
        val vm = viewModel()
        advanceTimeBy(401)
        dataSource.searchCalls.clear()
        userPreferences.writeError = DataError.Local.UNKNOWN

        vm.onAction(SearchAction.OnMaxFaceCountChange(10_000))
        vm.events.test {
            vm.onAction(SearchAction.OnMaxFaceCountChangeFinished)
            assertThat(awaitItem()).isInstanceOf(SearchEvent.ShowError::class)
        }

        assertThat(vm.state.value.maxFaceCount).isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
        assertThat(userPreferences.maxFaceCount.first()).isEqualTo(PolygonBudget.DEFAULT_MAX_FACES)
        assertThat(dataSource.searchCalls).hasSize(0)
    }

    @Test
    fun `budget usage of results is relative to the new threshold`() = runTest {
        dataSource.pageQueue += Result.Success(SearchPage(listOf(model("a", faceCount = 9_000)), null))
        dataSource.pageQueue += Result.Success(SearchPage(listOf(model("a", faceCount = 9_000)), null))
        val vm = viewModel()
        advanceTimeBy(401)
        assertThat(vm.state.value.models.single().budgetUsage).isEqualTo(9_000f / 30_000f)

        vm.onAction(SearchAction.OnMaxFaceCountChange(10_000))
        vm.onAction(SearchAction.OnMaxFaceCountChangeFinished)
        advanceTimeBy(1)

        assertThat(vm.state.value.models.single().budgetUsage).isEqualTo(0.9f)
    }

    @Test
    fun `slider values are clamped to the allowed range`() = runTest {
        val vm = viewModel()
        vm.onAction(SearchAction.OnMaxFaceCountChange(1))
        assertThat(vm.state.value.maxFaceCount).isEqualTo(PolygonBudget.MIN_FACES)
        vm.onAction(SearchAction.OnMaxFaceCountChange(1_000_000))
        assertThat(vm.state.value.maxFaceCount).isEqualTo(PolygonBudget.MAX_FACES)
    }

    @Test
    fun `budget sheet visibility toggles`() = runTest {
        val vm = viewModel()
        assertThat(vm.state.value.isBudgetSheetVisible).isFalse()
        vm.onAction(SearchAction.OnOpenBudgetSheet)
        assertThat(vm.state.value.isBudgetSheetVisible).isTrue()
        vm.onAction(SearchAction.OnDismissBudgetSheet)
        assertThat(vm.state.value.isBudgetSheetVisible).isFalse()
    }
}
