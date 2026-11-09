package com.fluxie.browser.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.ui.browser.BrowserViewModel
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentFilterScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BrowserViewModel(app)
        composeTestRule.setContent {
            FluxieTheme {
                ContentFilterScreen(navController = rememberNavController(), browserViewModel = viewModel)
            }
        }
    }

    @Test
    fun switchesAndImportAreShown() {
        show()
        composeTestRule.onNodeWithText("Adult content (18+) filter").assertIsDisplayed()
        composeTestRule.onNodeWithText("Block ads and trackers").assertIsDisplayed()
        composeTestRule.onNodeWithText("Import from text file").assertIsDisplayed()
    }

    @Test
    fun addedDomainAppearsInTheList() {
        show()
        composeTestRule.onNodeWithText("Add a domain, e.g. example.com").performTextInput("Blocked-Example.com")
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCount("blocked-example.com") > 0
        }
        composeTestRule.onNodeWithText("blocked-example.com").assertIsDisplayed()
    }

    @Test
    fun invalidDomainShowsError() {
        show()
        composeTestRule.onNodeWithText("Add a domain, e.g. example.com").performTextInput("not a domain")
        composeTestRule.onNodeWithText("Add").performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule.onAllNodesWithTextCount("That is not a valid domain") > 0
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
        onAllNodesWithText(text).fetchSemanticsNodes().size
}
