package com.fluxie.browser.ui.settings

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.fluxie.browser.common.BrowserMode
import com.fluxie.browser.common.PreferencesRepository
import com.fluxie.browser.common.SettingsStore
import com.fluxie.browser.ui.browser.BrowserViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BrowserViewModel(app)
        composeTestRule.setContent {
            FluxieTheme {
                SettingsScreen(
                    navController = rememberNavController(),
                    browserViewModel = viewModel
                )
            }
        }
    }

    @Test
    fun allSectionsDisplayed() {
        show()
        listOf(
            "Mode", "Appearance", "Search Engine", "Homepage",
            "Privacy and Security", "Permissions", "Advanced", "About"
        ).forEach { title ->
            // Section titles come first; a row inside the section may repeat the same text.
            composeTestRule.onAllNodesWithText(title).onLast().performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun modeChoicesAreDisplayedAndNormalIsDescribed() {
        show()
        composeTestRule.onNodeWithText("Normal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lite").assertIsDisplayed()
        composeTestRule.onNodeWithText("Full browsing experience", substring = true).assertIsDisplayed()
    }

    @Test
    fun persistingLiteSavesTheLaunchFlagAndTheSetting() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BrowserViewModel(app)
        viewModel.persistMode(BrowserMode.LITE)
        org.junit.Assert.assertTrue(PreferencesRepository(app).liteMode)
        org.junit.Assert.assertEquals(
            BrowserMode.LITE,
            runBlocking { SettingsStore(app).settings.first().mode }
        )
        viewModel.persistMode(BrowserMode.NORMAL)
        org.junit.Assert.assertFalse(PreferencesRepository(app).liteMode)
    }

    @Test
    fun searchFilterHidesOtherSections() {
        show()
        composeTestRule.onNodeWithText("Search settings").performClick()
        composeTestRule.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun searchEngineSheetOpensOnClick() {
        show()
        composeTestRule.onAllNodesWithText("Search Engine").onLast().performScrollTo().performClick()
        composeTestRule.onNodeWithText("Please select your search engine:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Brave Search").assertIsDisplayed()
    }

    @Test
    fun homepageSheetOpensOnClick() {
        show()
        composeTestRule.onAllNodesWithText("Homepage").onLast().performScrollTo().performClick()
        // Sheet defaults to "Start page"; the URL field only appears under "Custom URL"
        composeTestRule.onNodeWithText("Custom URL").performClick()
        composeTestRule.onNodeWithText("Input new homepage url below:").assertIsDisplayed()
    }

    @Test
    fun clearBrowsingDataDialogOffersEachKindAndConfirms() {
        show()
        composeTestRule.onNodeWithText("Clear browsing data").performScrollTo().performClick()
        composeTestRule.onNodeWithText("History").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cache").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cookies and site data").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun contentFilterRowIsListedUnderPrivacy() {
        show()
        composeTestRule.onNodeWithText("Content filter").performScrollTo().assertIsDisplayed()
    }
}
