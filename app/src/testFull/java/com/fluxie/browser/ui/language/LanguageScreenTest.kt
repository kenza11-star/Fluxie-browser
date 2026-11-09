package com.fluxie.browser.ui.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.fluxie.browser.ui.theme.FluxieTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LanguageScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allLanguageOptionsDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                LanguageScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Follow System").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("简体中文").assertIsDisplayed()
    }

    @Test
    fun languageTitleDisplayed() {
        composeTestRule.setContent {
            FluxieTheme {
                LanguageScreen(onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Language").assertIsDisplayed()
    }
}
