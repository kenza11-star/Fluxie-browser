package com.fluxie.browser.ui.about

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AboutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                AboutScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Fluxie").assertIsDisplayed()
    }

    @Test
    fun versionTextDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                AboutScreen(onBack = {})
            }
        }

        // Version format includes "Version:" prefix
        composeTestRule.onNodeWithText("Version:", substring = true).assertIsDisplayed()
    }

    @Test
    fun copyrightDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                AboutScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Copyright", substring = true).assertIsDisplayed()
    }

    @Test
    fun aboutTitleDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                AboutScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("About").assertIsDisplayed()
    }
}
