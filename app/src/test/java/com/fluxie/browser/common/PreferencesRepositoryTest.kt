package com.fluxie.browser.common

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesRepositoryTest {

    private lateinit var prefs: PreferencesRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = PreferencesRepository(context)
    }

    @Test
    fun `default homepage is google`() {
        assertEquals("www.google.com", prefs.homepage)
    }

    @Test
    fun `set and get homepage`() {
        prefs.homepage = "www.example.com"
        assertEquals("www.example.com", prefs.homepage)
    }

    @Test
    fun `default search engine is google`() {
        assertEquals("GOOGLE", prefs.searchEngineChoice)
        assertEquals(SearchEngine.GOOGLE.template, prefs.searchTemplate())
        assertEquals("Google", prefs.searchDisplayName())
    }

    @Test
    fun `preset choice is stored and used for the template`() {
        prefs.searchEngineChoice = SearchEngine.BRAVE.name
        assertEquals(SearchEngine.BRAVE.template, prefs.searchTemplate())
        assertEquals("Brave Search", prefs.searchDisplayName())
    }

    @Test
    fun `custom template is used when selected and valid`() {
        prefs.customSearchTemplate = "https://example.com/s?q=%s"
        prefs.searchEngineChoice = CUSTOM_SEARCH
        assertEquals("https://example.com/s?q=%s", prefs.searchTemplate())
        assertEquals("example.com", prefs.searchDisplayName())
    }

    @Test
    fun `custom template is remembered while a preset is selected`() {
        prefs.customSearchTemplate = "https://example.com/s?q=%s"
        prefs.searchEngineChoice = SearchEngine.BING.name
        assertEquals(SearchEngine.BING.template, prefs.searchTemplate())
        assertEquals("https://example.com/s?q=%s", prefs.customSearchTemplate)
    }

    @Test
    fun `invalid custom template falls back to google`() {
        prefs.customSearchTemplate = "nonsense"
        prefs.searchEngineChoice = CUSTOM_SEARCH
        assertEquals(SearchEngine.GOOGLE.template, prefs.searchTemplate())
    }

    @Test
    fun `lite mode flag defaults to off and persists`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(false, prefs.liteMode)
        prefs.liteMode = true
        assertEquals(true, PreferencesRepository(context).liteMode)
        prefs.liteMode = false
        assertEquals(false, PreferencesRepository(context).liteMode)
    }
}
