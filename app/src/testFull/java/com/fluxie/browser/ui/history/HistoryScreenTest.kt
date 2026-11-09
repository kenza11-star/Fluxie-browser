package com.fluxie.browser.ui.history

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.data.FluxieDatabase
import com.fluxie.browser.data.HistoryRepository
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createViewModel(): HistoryViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val db = FluxieDatabase.getInstance(app)
        return HistoryViewModel(HistoryRepository(db.historyDao()))
    }

    @Test
    fun historyScreenDisplaysTitle() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            FluxieTheme {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onNavigate = {}
                )
            }
        }

        composeTestRule.onNodeWithText("History").assertIsDisplayed()
    }

    @Test
    fun emptyHistoryShowsMessage() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            FluxieTheme {
                HistoryScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onNavigate = {}
                )
            }
        }

        composeTestRule.onNodeWithText("No browsing history").assertIsDisplayed()
    }
}
