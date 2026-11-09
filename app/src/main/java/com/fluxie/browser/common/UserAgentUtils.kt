package com.fluxie.browser.common

object UserAgentUtils {

    private val ANDROID_PLATFORM = Regex("\\(Linux; Android[^)]*\\)")
    private val VERSION_TOKEN = Regex("\\s+Version/[\\d.]+")
    private val MOBILE_TOKEN = Regex("\\s+Mobile(?=\\s|$)")

    /**
     * Turns the device's mobile WebView user agent into a desktop Linux Chrome one: swaps the
     * Android platform block for X11, and drops the WebView-only "Version/x" and "Mobile" tokens.
     * An agent that has none of those parts is returned unchanged.
     */
    fun toDesktop(mobileUserAgent: String): String =
        mobileUserAgent
            .replace(ANDROID_PLATFORM, "(X11; Linux x86_64)")
            .replace(VERSION_TOKEN, "")
            .replace(MOBILE_TOKEN, "")
}
