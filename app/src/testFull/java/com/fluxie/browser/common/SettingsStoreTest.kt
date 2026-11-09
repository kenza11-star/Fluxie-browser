package com.fluxie.browser.common

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun storeOn(file: File, scope: CoroutineScope) =
        SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file })

    @Test
    fun defaultsWhenNothingStored() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val s = storeOn(File(tmp.newFolder(), "s.preferences_pb"), scope).settings.first()
        assertEquals(AppSettings(), s)
        assertEquals(false, s.adultFilter) // the adult filter is off until the user turns it on
        assertEquals(true, s.adBlock)
        scope.cancel()
    }

    @Test
    fun valuesSurviveRestart() = runBlocking {
        val file = File(tmp.newFolder(), "s.preferences_pb")

        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store1 = storeOn(file, first)
        store1.setMode(BrowserMode.LITE)
        store1.setThemeMode(ThemeMode.DARK)
        store1.setDynamicColor(true)
        store1.setAdultFilter(true)
        store1.setAdBlock(false)
        first.cancel() // the "process" dies; only the file remains

        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val restored = storeOn(file, second).settings.first()
        assertEquals(AppSettings(BrowserMode.LITE, ThemeMode.DARK, true, adultFilter = true, adBlock = false), restored)
        second.cancel()
    }

    @Test
    fun switchingModeKeepsOtherSettings() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = storeOn(File(tmp.newFolder(), "s.preferences_pb"), scope)
        store.setThemeMode(ThemeMode.LIGHT)
        store.setMode(BrowserMode.LITE)
        store.setMode(BrowserMode.NORMAL)
        assertEquals(AppSettings(BrowserMode.NORMAL, ThemeMode.LIGHT, false), store.settings.first())
        scope.cancel()
    }
}
