package com.mmunoz.filamentpokemon.search.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.core.domain.model.PolygonBudget
import com.mmunoz.filamentpokemon.core.presentation.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.NumberFormat

@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val robot by lazy { SearchScreenRobot(composeRule) }

    private fun model(
        uid: String,
        name: String = "Pokemon $uid",
        faceCount: Int = 10_000,
        isAnimated: Boolean = false,
        maxFaceCount: Int = PolygonBudget.DEFAULT_MAX_FACES
    ) = PokemonModelUi(
        uid = uid,
        name = name,
        author = "trainer-$uid",
        thumbnailUrl = null,
        faceCount = faceCount,
        formattedFaceCount = NumberFormat.getIntegerInstance().format(faceCount),
        budgetUsage = faceCount.toFloat() / maxFaceCount,
        isAnimated = isAnimated,
        licenseLabel = "CC Attribution"
    )

    private val fewModels = listOf(
        model("1", name = "Pikachu", faceCount = 4_500, isAnimated = true),
        model("2", name = "Charizard", faceCount = 21_300),
        model("3", name = "Eevee", faceCount = 9_800)
    )

    private fun manyModels(count: Int = 40) = List(count) { model(uid = "uid-$it", faceCount = 1_000 * (it + 1)) }

    private fun pagedState(models: List<PokemonModelUi>, endReached: Boolean = false) =
        SearchState(models = models, nextCursor = "24", endReached = endReached)

    @Test
    fun grid_rendersOneCardPerModel_withNameAndFaceCountChip() {
        robot.setContent(SearchState(models = fewModels)).assertCardCount(fewModels.size)
        fewModels.forEach { robot.assertCardShows(it) }
        robot.assertNoProgressIndicator()
    }

    @Test
    fun grid_tappingCard_dispatchesOnModelClick() {
        robot
            .setContent(SearchState(models = fewModels))
            .clickCard(fewModels[1])
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnModelClick("2")), robot.actions)
    }

    @Test
    fun loading_showsProgressIndicator_andNoCards() {
        robot
            .setContent(SearchState(isLoading = true))
            .assertProgressIndicatorShown()
            .assertCardCount(0)
            .assertTextAbsent(robot.string(R.string.search_empty))
    }

    @Test
    fun error_showsMessage_andRetryDispatchesOnRetry() {
        val message = robot.string(R.string.error_no_internet)

        robot
            .setContent(SearchState(error = UiText.StringResource(R.string.error_no_internet)))
            .assertTextShown(message)
            .assertCardCount(0)
            .clickRetry()
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnRetry), robot.actions)
    }

    @Test
    fun emptyResult_showsEmptyMessage() {
        robot
            .setContent(SearchState(query = "mewthree", models = emptyList()))
            .assertTextShown(robot.string(R.string.search_empty))
            .assertCardCount(0)
            .assertNoProgressIndicator()
    }

    @Test
    fun typingInSearchField_dispatchesOnQueryChange() {
        robot
            .setContent(SearchState())
            .typeQuery("pika")
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnQueryChange("pika")), robot.actions)
    }

    @Test
    fun clearIcon_dispatchesOnClearQuery() {
        robot
            .setContent(SearchState(query = "pika"))
            .clickClearQuery()
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnClearQuery), robot.actions)
    }

    @Test
    fun clearIcon_isHidden_whileQueryIsEmpty() {
        robot.setContent(SearchState(query = "")).assertClearQueryAbsent()
    }

    @Test
    fun imeSearchAction_dispatchesOnSearchSubmit() {
        robot
            .setContent(SearchState(query = "pika"))
            .submitQuery()
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnSearchSubmit), robot.actions)
    }

    @Test
    fun loadMore_dispatchedOnce_whenGridEndIsVisibleWithoutScrolling() {
        robot
            .setContent(pagedState(fewModels))
            .waitForAction(SearchAction.OnLoadMore)
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnLoadMore), robot.actions)
    }

    @Test
    fun loadMore_dispatchedOnce_whenGridScrollsToLastItem() {
        val models = manyModels()
        robot.setContent(pagedState(models)).waitForIdle()
        assertFalse(SearchAction.OnLoadMore in robot.actions)

        robot
            .scrollGridToIndex(models.lastIndex)
            .waitForAction(SearchAction.OnLoadMore)
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnLoadMore), robot.actions)
    }

    @Test
    fun loadMore_notDispatched_whenEndReached() {
        val models = manyModels()
        robot
            .setContent(pagedState(models, endReached = true))
            .scrollGridToIndex(models.lastIndex)
            .waitForIdle()

        assertEquals(emptyList<SearchAction>(), robot.actions)
    }

    @Test
    fun loadingMore_showsProgressIndicator_belowCards() {
        robot
            .setContent(pagedState(fewModels).copy(isLoadingMore = true))
            .assertCardCount(fewModels.size)
            .scrollGridToIndex(fewModels.size)
            .assertProgressIndicatorShown()
    }

    @Test
    fun budgetLabel_showsMaxFaceCount() {
        robot.setContent(SearchState(maxFaceCount = 20_000)).assertBudgetLabel(20_000)
    }

    @Test
    fun budgetSettingsIcon_dispatchesOnOpenBudgetSheet() {
        robot
            .setContent(SearchState())
            .clickBudgetSettings()
            .waitForIdle()

        assertEquals(listOf(SearchAction.OnOpenBudgetSheet), robot.actions)
    }

    @Test
    fun budgetSheet_showsTitleAndValue_whenVisible() {
        robot
            .setContent(SearchState(maxFaceCount = 20_000, isBudgetSheetVisible = true))
            .assertBudgetSheetShown(20_000)
    }
}
