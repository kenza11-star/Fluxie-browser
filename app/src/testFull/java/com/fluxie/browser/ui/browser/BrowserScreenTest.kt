package com.fluxie.browser.ui.browser

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createViewModel(): BrowserViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        return BrowserViewModel(app)
    }

    @Test
    fun tabCountButtonShowsCorrectCount() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            FluxieTheme {
                BrowserScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToHistory = {},
                    onNavigateToBookmarks = {},
                    onNavigateToDownloads = {},
                    onNavigateToContentFilter = {}
                )
            }
        }

        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun tabCountUpdatesAfterAddingTab() {
        val viewModel = createViewModel()

        composeTestRule.setContent {
            FluxieTheme {
                BrowserScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = {},
                    onNavigateToHistory = {},
                    onNavigateToBookmarks = {},
                    onNavigateToDownloads = {},
                    onNavigateToContentFilter = {}
                )
            }
        }

        viewModel.addTab()
        composeTestRule.onNodeWithText("2").assertIsDisplayed()
    }
}
