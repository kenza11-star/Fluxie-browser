package com.fluxie.browser.common

enum class BrowserMode { NORMAL, LITE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Settings kept in DataStore. [dynamicColor] is off by default so the Fluxie palette shows.
 * [adultFilter] is off by default and only ever turned on by the user. [adBlock] is on by default.
 */
data class AppSettings(
    val mode: BrowserMode = BrowserMode.NORMAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val adultFilter: Boolean = false,
    val adBlock: Boolean = true
)

/** Reads a stored enum name, falling back to [default] for missing or unknown values. */
inline fun <reified T : Enum<T>> parseEnum(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
