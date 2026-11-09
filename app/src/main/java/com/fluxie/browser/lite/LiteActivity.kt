package com.fluxie.browser.lite

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.fluxie.browser.R
import com.fluxie.browser.common.BlockRules
import com.fluxie.browser.common.BlocklistParser
import com.fluxie.browser.common.ContentBlocker
import com.fluxie.browser.common.PreferencesRepository
import com.fluxie.browser.common.TrimPolicy
import com.fluxie.browser.common.UrlUtils
import java.io.ByteArrayInputStream
import java.io.IOException

/** One Lite tab. The WebView exists only while the tab is visible or recently used (see [onTrimMemory]). */
private class LiteTab(val id: Int) {
    var webView: WebView? = null
    var savedState: Bundle? = null
    var url: String = ""
    var title: String = ""
}

/**
 * Lite mode: a small browser built from plain framework Views (no Compose, Room, DataStore or
 * AppCompat). Up to [LiteTabList.MAX_TABS] tabs, address bar, back/forward/reload/home, bookmarks in
 * SharedPreferences and a short built-in ad rule list. There is deliberately no tab grid, history,
 * downloads manager or find-in-page here.
 */
class LiteActivity : Activity() {

    private lateinit var prefs: PreferencesRepository
    private lateinit var bookmarks: LiteBookmarkStore

    private val tabList = LiteTabList()
    private val tabs = HashMap<Int, LiteTab>().apply { put(0, LiteTab(0)) }

    private lateinit var rootView: LinearLayout
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var webContainer: FrameLayout
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var reloadButton: ImageButton
    private lateinit var tabCountView: TextView
    private var loading = false

    /** Built-in ad/tracker domains; read on first use so launching does not pay for it. */
    private val adRules: BlockRules by lazy {
        BlockRules(adTrackerDomains = loadAdDomains(), adultFilterEnabled = false, adBlockEnabled = true)
    }

    private val activeTab: LiteTab get() = tabs.getValue(tabList.activeId)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesRepository(this)
        bookmarks = LiteBookmarkStore(this)
        setContentView(buildLayout())
        registerBackHandler()

        val link = intent?.dataString?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (link != null) loadInActiveTab(link) else goHome()
        refreshChrome()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.takeIf { it.startsWith("http", ignoreCase = true) }?.let { loadInActiveTab(it) }
    }

    override fun onResume() {
        super.onResume()
        activeTab.webView?.onResume()
    }

    override fun onPause() {
        activeTab.webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        tabs.values.forEach { destroyWebView(it) }
        super.onDestroy()
    }

    // ---- Memory ----

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (TrimPolicy.shouldRelease(level)) releaseBackgroundTabs()
    }

    /** Frees the WebViews of tabs that are not on screen; they reload from saved state when opened again. */
    private fun releaseBackgroundTabs() {
        val alive = tabs.values.filter { it.webView != null }.map { it.id }.toSet()
        for (id in TrimPolicy.tabsToRelease(tabList.ids, tabList.activeId, alive)) {
            val tab = tabs[id] ?: continue
            val webView = tab.webView ?: continue
            val state = Bundle()
            webView.saveState(state)
            tab.savedState = state
            webView.url?.takeIf { isWebUrl(it) }?.let { tab.url = it }
            destroyWebView(tab)
        }
        refreshChrome()
    }

    // ---- Layout ----

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.fluxie_lite_background))
            // Take focus first so the address bar is not focused (and showing a raw URL) at launch.
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
            setOnApplyWindowInsetsListener { v, insets ->
                // Edge-to-edge is enforced on new Android versions: keep content clear of the system bars.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    v.setPadding(
                        insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom
                    )
                }
                insets
            }
        }

        // Top bar: address pill + menu button.
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(4), dp(8))
        }
        addressBar = EditText(this).apply {
            hint = getString(R.string.lite_address_hint)
            setHintTextColor(color(R.color.fluxie_lite_text_dim))
            setTextColor(color(R.color.fluxie_lite_text))
            textSize = 15f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            background = rounded(color(R.color.fluxie_lite_surface), dp(24))
            setPadding(dp(18), dp(10), dp(18), dp(10))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    submitAddress()
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, focused ->
                if (focused) {
                    setText(activeTab.url.takeIf { isWebUrl(it) } ?: "")
                    selectAll()
                } else {
                    showAddressText()
                }
            }
        }
        top.addView(addressBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(iconButton(R.drawable.ic_lite_more, R.string.lite_cd_menu) { showMenu() }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressDrawable.setColorFilter(color(R.color.fluxie_lite_accent), PorterDuff.Mode.SRC_IN)
        }
        root.addView(progressBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))

        webContainer = FrameLayout(this)
        root.addView(webContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom bar: back, forward, reload/stop, home, bookmarks, tabs.
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(color(R.color.fluxie_lite_surface), dp(0))
        }
        backButton = iconButton(R.drawable.ic_lite_back, R.string.lite_cd_back) { activeTab.webView?.goBack() }
        forwardButton = iconButton(R.drawable.ic_lite_forward, R.string.lite_cd_forward) { activeTab.webView?.goForward() }
        reloadButton = iconButton(R.drawable.ic_lite_reload, R.string.lite_cd_reload) {
            val webView = activeTab.webView
            if (webView == null) {
                goHome()
            } else if (loading) {
                webView.stopLoading()
            } else {
                webView.reload()
            }
        }
        val homeButton = iconButton(R.drawable.ic_lite_home, R.string.lite_cd_home) { goHome() }
        val bookmarksButton = iconButton(R.drawable.ic_lite_bookmarks, R.string.lite_cd_bookmarks) { showBookmarks() }
        tabCountView = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(color(R.color.fluxie_lite_text))
            contentDescription = getString(R.string.lite_cd_tabs)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setStroke(dp(2), color(R.color.fluxie_lite_text))
                setColor(Color.TRANSPARENT)
            }
            setOnClickListener { showTabs() }
        }
        val barItem = { LinearLayout.LayoutParams(0, dp(52), 1f) }
        listOf(backButton, forwardButton, reloadButton, homeButton, bookmarksButton).forEach { bottom.addView(it, barItem()) }
        val tabHolder = FrameLayout(this)
        tabHolder.addView(tabCountView, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        tabHolder.setOnClickListener { showTabs() }
        bottom.addView(tabHolder, barItem())
        root.addView(bottom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        rootView = root
        return root
    }

    private fun color(res: Int): Int = resources.getColor(res, theme)

    private fun rounded(fill: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
    }

    private fun iconButton(icon: Int, description: Int, onClick: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setColorFilter(color(R.color.fluxie_lite_text), PorterDuff.Mode.SRC_IN)
        contentDescription = getString(description)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { onClick() }
    }

    // ---- Navigation ----

    private fun submitAddress() {
        val url = UrlUtils.resolveInput(addressBar.text.toString(), prefs.searchTemplate())
        hideKeyboard()
        rootView.requestFocus()
        if (url.isNotEmpty()) loadInActiveTab(url)
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(addressBar.windowToken, 0)
    }

    private fun loadInActiveTab(url: String) {
        webViewFor(activeTab).loadUrl(url)
        activeTab.url = url
        showAddressText()
    }

    /** Home is the user's homepage, or a tiny local page listing the bookmarks. */
    private fun goHome() {
        val tab = activeTab
        if (prefs.homeButtonGoesToStartPage) {
            val html = LiteStartPage.html(
                getString(R.string.lite_start_title),
                getString(R.string.lite_start_subtitle),
                getString(R.string.lite_start_empty),
                bookmarks.all()
            )
            webViewFor(tab).loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            tab.url = ""
            tab.title = ""
            showAddressText()
        } else {
            val url = UrlUtils.resolveInput(prefs.homepage, prefs.searchTemplate())
            if (url.isNotEmpty()) loadInActiveTab(url)
        }
    }

    private fun handleBack() {
        val webView = activeTab.webView
        when {
            webView != null && webView.canGoBack() -> webView.goBack()
            tabList.size > 1 -> closeTab(tabList.activeId)
            else -> finish()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    private fun registerBackHandler() {
        // With targetSdk 36+ the system calls this callback instead of onBackPressed().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                android.window.OnBackInvokedCallback { handleBack() }
            )
        }
    }

    // ---- Tabs ----

    private fun showTabs() {
        val ids = tabList.ids
        val titles = ids.map { id ->
            val tab = tabs.getValue(id)
            tab.title.ifBlank { tab.url }.ifBlank { getString(R.string.lite_untitled) }
        }.toTypedArray<CharSequence>()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.lite_tabs_title, tabList.size, tabList.maxTabs))
            .setSingleChoiceItems(titles, ids.indexOf(tabList.activeId)) { dialog, which ->
                dialog.dismiss()
                switchTo(ids[which])
            }
            .setPositiveButton(R.string.lite_new_tab) { _, _ -> newTab() }
            .setNegativeButton(R.string.lite_close_tab) { _, _ -> closeTab(tabList.activeId) }
            .show()
    }

    private fun newTab() {
        val id = tabList.add()
        if (id == null) {
            toast(getString(R.string.lite_tab_limit, tabList.maxTabs))
            return
        }
        tabs[id] = LiteTab(id)
        showActiveTab()
        goHome()
    }

    private fun switchTo(id: Int) {
        if (!tabList.activate(id)) return
        showActiveTab()
    }

    private fun closeTab(id: Int) {
        tabs[id]?.let { destroyWebView(it) }
        tabs.remove(id)
        val active = tabList.close(id)
        // Closing the last tab makes the list create a fresh one; give it a screen too.
        val isFresh = tabs[active] == null
        if (isFresh) tabs[active] = LiteTab(active)
        showActiveTab()
        if (isFresh) goHome()
    }

    /** Puts the active tab's WebView on screen, creating or restoring it if it was released. */
    private fun showActiveTab() {
        webContainer.removeAllViews()
        tabs.values.forEach { it.webView?.onPause() }
        val webView = webViewFor(activeTab)
        webContainer.addView(webView)
        webView.onResume()
        loading = false
        refreshChrome()
        showAddressText()
    }

    // ---- WebViews ----

    private fun webViewFor(tab: LiteTab): WebView {
        tab.webView?.let {
            if (it.parent == null && tab.id == tabList.activeId) webContainer.addView(it)
            return it
        }
        val webView = createWebView(tab)
        tab.webView = webView
        val state = tab.savedState
        tab.savedState = null
        if (state != null) {
            if (webView.restoreState(state) == null && tab.url.isNotEmpty()) webView.loadUrl(tab.url)
        }
        if (tab.id == tabList.activeId) webContainer.addView(webView)
        return webView
    }

    private fun destroyWebView(tab: LiteTab) {
        val webView = tab.webView ?: return
        tab.webView = null
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(tab: LiteTab): WebView = WebView(this).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(color(R.color.fluxie_lite_background))
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request ?: return null
                val blocked = ContentBlocker.decide(request.url.toString(), adRules, request.isForMainFrame) != null
                return if (blocked) {
                    WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                } else {
                    null
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                request != null && handleExternalScheme(request.url)

            @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                url != null && handleExternalScheme(Uri.parse(url))

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (url != null && isWebUrl(url)) tab.url = url
                if (tab.id == tabList.activeId) {
                    loading = true
                    progressBar.visibility = View.VISIBLE
                    refreshChrome()
                    showAddressText()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != null && isWebUrl(url)) tab.url = url
                tab.title = view?.title.orEmpty().takeUnless { it == "about:blank" } ?: ""
                if (tab.id == tabList.activeId) {
                    loading = false
                    progressBar.visibility = View.GONE
                    refreshChrome()
                    showAddressText()
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (tab.id == tabList.activeId) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                tab.title = title.orEmpty()
            }
        }
        // Lite has no downloads manager: a download is handed to another app (e.g. the system downloader).
        setDownloadListener { url, _, _, _, _ ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                toast(getString(R.string.lite_download_external))
            } catch (_: ActivityNotFoundException) {
                toast(getString(R.string.lite_download_no_app))
            }
        }
    }

    /** Links such as mailto: and tel: go to the matching app; other non-web schemes are not loaded. */
    private fun handleExternalScheme(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data") return false
        if (scheme in EXTERNAL_SCHEMES) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                // No app for it: nothing to open.
            }
        }
        return true
    }

    private fun loadAdDomains(): Set<String> = try {
        assets.open(AD_RULES_ASSET).bufferedReader().use { BlocklistParser.parse(it.readText()).domains.toSet() }
    } catch (_: IOException) {
        emptySet()
    }

    // ---- Chrome (buttons, address text) ----

    private fun refreshChrome() {
        val webView = activeTab.webView
        backButton.alpha = if (webView?.canGoBack() == true) 1f else 0.35f
        forwardButton.alpha = if (webView?.canGoForward() == true) 1f else 0.35f
        reloadButton.setImageResource(if (loading) R.drawable.ic_lite_stop else R.drawable.ic_lite_reload)
        reloadButton.contentDescription = getString(if (loading) R.string.lite_cd_stop else R.string.lite_cd_reload)
        tabCountView.text = tabList.size.toString()
    }

    private fun showAddressText() {
        if (addressBar.hasFocus()) return
        val url = activeTab.url
        addressBar.setText(if (isWebUrl(url)) UrlUtils.extractDomain(url) else "")
    }

    // ---- Bookmarks and menu ----

    private fun currentPageUrl(): String? = activeTab.url.takeIf { isWebUrl(it) }

    private fun showMenu() {
        val url = currentPageUrl()
        val bookmarked = url != null && bookmarks.contains(url)
        val labels = ArrayList<CharSequence>()
        val actions = ArrayList<() -> Unit>()
        labels += getString(if (bookmarked) R.string.lite_remove_bookmark else R.string.lite_add_bookmark)
        actions += { toggleBookmark() }
        if (!resources.getBoolean(R.bool.is_lite_edition)) {
            labels += getString(R.string.lite_switch_to_normal)
            actions += { switchToNormal() }
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun toggleBookmark() {
        val url = currentPageUrl()
        if (url == null) {
            toast(getString(R.string.lite_bookmark_no_page))
            return
        }
        if (bookmarks.contains(url)) {
            bookmarks.remove(url)
            toast(getString(R.string.lite_bookmark_removed))
        } else {
            val added = bookmarks.add(activeTab.title, url)
            toast(getString(if (added) R.string.lite_bookmark_added else R.string.lite_bookmark_exists))
        }
    }

    private fun showBookmarks() {
        val items = bookmarks.all()
        if (items.isEmpty()) {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.lite_bookmarks_title)
                .setMessage(R.string.lite_bookmarks_empty)
                .setPositiveButton(R.string.lite_close, null)
                .show()
            return
        }
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.lite_bookmarks_title)
            .setItems(items.map { it.title.ifBlank { it.url } }.toTypedArray<CharSequence>()) { _, which ->
                loadInActiveTab(items[which].url)
            }
            .setPositiveButton(R.string.lite_close, null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                bookmarks.remove(items[position].url)
                toast(getString(R.string.lite_bookmark_removed))
                dialog.dismiss()
                true
            }
        }
        dialog.show()
        toast(getString(R.string.lite_bookmark_hint))
    }

    private fun switchToNormal() {
        prefs.liteMode = false
        ModeSwitch.openNormal(this)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    private companion object {
        const val AD_RULES_ASSET = "ad_tracker_domains.txt"
        val EXTERNAL_SCHEMES = setOf("mailto", "tel", "sms", "geo")
    }
}
