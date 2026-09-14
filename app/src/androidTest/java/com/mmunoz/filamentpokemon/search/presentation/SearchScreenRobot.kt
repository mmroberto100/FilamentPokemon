package com.mmunoz.filamentpokemon.search.presentation

import android.content.Context
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.mmunoz.filamentpokemon.R
import com.mmunoz.filamentpokemon.ui.theme.FilamentPokemonTheme
import java.text.NumberFormat

/**
 * Drives the stateless [SearchScreen] through its public semantics only (visible text,
 * content descriptions, roles) and records every [SearchAction] the screen dispatches.
 */
class SearchScreenRobot(private val rule: ComposeContentTestRule) {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    val actions = mutableListOf<SearchAction>()

    private val indeterminateProgress = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    /** A grid card is the clickable node that merged its thumbnail's "Preview of …" description. */
    private val anyCard =
        hasClickAction() and hasContentDescription(string(R.string.cd_model_thumbnail, ""), substring = true)

    private fun cardMatcher(model: PokemonModelUi) =
        hasClickAction() and hasContentDescription(string(R.string.cd_model_thumbnail, model.name))

    private fun card(model: PokemonModelUi) = rule.onNode(cardMatcher(model))

    private fun searchField() = rule.onNode(hasSetTextAction())

    private fun grid() = rule.onNode(hasScrollToIndexAction())

    fun string(id: Int, vararg args: Any): String = context.getString(id, *args)

    fun setContent(state: SearchState) = apply {
        rule.setContent {
            FilamentPokemonTheme {
                SearchScreen(state = state, onAction = { actions += it })
            }
        }
    }

    fun assertCardCount(count: Int) = apply {
        rule.onAllNodes(anyCard).assertCountEquals(count)
    }

    fun assertCardShows(model: PokemonModelUi) = apply {
        grid().performScrollToNode(cardMatcher(model))
        card(model)
            .assertIsDisplayed()
            .assert(hasText(model.name))
            .assert(hasText(string(R.string.search_faces_chip, model.formattedFaceCount)))
    }

    fun clickCard(model: PokemonModelUi) = apply {
        card(model).performClick()
    }

    fun assertProgressIndicatorShown() = apply {
        rule.onNode(indeterminateProgress).assertIsDisplayed()
    }

    fun assertNoProgressIndicator() = apply {
        rule.onAllNodes(indeterminateProgress).assertCountEquals(0)
    }

    fun assertTextShown(text: String) = apply {
        rule.onNodeWithText(text).assertIsDisplayed()
    }

    fun assertTextAbsent(text: String) = apply {
        rule.onAllNodesWithText(text).assertCountEquals(0)
    }

    fun clickRetry() = apply {
        rule.onNodeWithText(string(R.string.search_retry)).performClick()
    }

    fun typeQuery(text: String) = apply {
        searchField().performTextInput(text)
    }

    fun submitQuery() = apply {
        searchField().performImeAction()
    }

    fun clickClearQuery() = apply {
        rule.onNodeWithContentDescription(string(R.string.cd_clear_query)).performClick()
    }

    fun assertClearQueryAbsent() = apply {
        rule.onAllNodes(hasContentDescription(string(R.string.cd_clear_query))).assertCountEquals(0)
    }

    fun clickBudgetSettings() = apply {
        rule.onNodeWithContentDescription(string(R.string.cd_budget_settings)).performClick()
    }

    fun assertBudgetLabel(maxFaceCount: Int) = apply {
        assertTextShown(string(R.string.search_budget_label, formatted(maxFaceCount)))
    }

    fun assertBudgetSheetShown(maxFaceCount: Int) = apply {
        val title = string(R.string.budget_sheet_title)
        rule.waitUntil(SHEET_TIMEOUT_MS) {
            rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.budget_sheet_value, formatted(maxFaceCount))).assertIsDisplayed()
    }

    fun scrollGridToIndex(index: Int) = apply {
        grid().performScrollToIndex(index)
    }

    fun waitForIdle() = apply {
        rule.waitForIdle()
    }

    fun waitForAction(action: SearchAction) = apply {
        rule.waitUntil(ACTION_TIMEOUT_MS) { action in actions }
    }

    private fun formatted(faceCount: Int): String = NumberFormat.getIntegerInstance().format(faceCount)

    private companion object {
        const val ACTION_TIMEOUT_MS = 5_000L
        const val SHEET_TIMEOUT_MS = 5_000L
    }
}
