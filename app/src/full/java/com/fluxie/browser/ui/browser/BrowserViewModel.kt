package com.fluxie.browser.ui.browser

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebStorage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.fluxie.browser.R
import com.fluxie.browser.common.AppSettings
import com.fluxie.browser.common.BlockPage
import com.fluxie.browser.common.BlockReason
import com.fluxie.browser.common.BlockRules
import com.fluxie.browser.common.BlockedCounts
import com.fluxie.browser.common.BlocklistParser
import com.fluxie.browser.common.BrowserMode
import com.fluxie.browser.common.ContentBlocker
import com.fluxie.browser.common.SafeSearch
import com.fluxie.browser.common.TrimPolicy
import com.fluxie.browser.lite.LiteBookmarkStore
import com.fluxie.browser.tab.Tab
import com.fluxie.browser.common.PreferencesRepository
import com.fluxie.browser.common.SettingsStore
import com.fluxie.browser.common.ThemeMode
import com.fluxie.browser.common.SearchEngine
import com.fluxie.browser.common.UrlUtils
import com.fluxie.browser.common.UserAgentUtils
import com.fluxie.browser.data.AndroidDownloadBackend
import com.fluxie.browser.data.DownloadRepository
import com.fluxie.browser.data.DownloadRequest
import com.fluxie.browser.data.BlockCategory
import com.fluxie.browser.data.BlockedDomain
import com.fluxie.browser.data.BlockedDomainRepository
import com.fluxie.browser.data.BookmarkRepository
import com.fluxie.browser.data.HistoryRepository
import com.fluxie.browser.data.FluxieDatabase
import com.fluxie.browser.data.LiteBookmarkSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.fluxie.browser.tab.TabManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Find-in-page result: [active] is 1-based (0 when nothing matched), [total] the match count. */
data class FindResult(val active: Int = 0, val total: Int = 0)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    val prefs = PreferencesRepository(application)
    val tabManager = TabManager()
    private val historyRepository: HistoryRepository
    private val bookmarkRepository: BookmarkRepository
    private val downloadRepository: DownloadRepository
    val blockedDomainRepository: BlockedDomainRepository

    // ---- Content filter state (read from WebView threads, so volatile / thread-safe) ----
    @Volatile
    private var blockRules = BlockRules()
    private val blockedCounts = BlockedCounts()

    private val _blockedCount = MutableStateFlow(0)

    /** Requests blocked so far on the page currently shown in the active tab. */
    val blockedCount: StateFlow<Int> = _blockedCount.asStateFlow()

    // One WebView per tab, keyed by tab id
    private val webViews = mutableMapOf<Int, WebView>()

    /** Saved back/forward state of tabs whose WebView was released on low memory (see [releaseBackgroundTabs]). */
    private val discardedStates = mutableMapOf<Int, Bundle>()

    private val _activeTabId = MutableStateFlow(0)
    val activeTabId: StateFlow<Int> = _activeTabId.asStateFlow()

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _loadingProgress = MutableStateFlow(-1f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _canGoForward = MutableStateFlow(false)
    val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

    private val _tabCount = MutableStateFlow(1)
    val tabCount: StateFlow<Int> = _tabCount.asStateFlow()

    private val _favicon = MutableStateFlow<Bitmap?>(null)
    val favicon: StateFlow<Bitmap?> = _favicon.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _findInPageActive = MutableStateFlow(false)
    val findInPageActive: StateFlow<Boolean> = _findInPageActive.asStateFlow()

    private val _showTabGrid = MutableStateFlow(false)
    val showTabGrid: StateFlow<Boolean> = _showTabGrid.asStateFlow()

    /** True while the active tab is an incognito tab. */
    private val _isIncognito = MutableStateFlow(false)
    val isIncognito: StateFlow<Boolean> = _isIncognito.asStateFlow()

    private val _findResult = MutableStateFlow(FindResult())
    val findResult: StateFlow<FindResult> = _findResult.asStateFlow()

    /** True while the active tab asks sites for their desktop version. */
    private val _desktopSite = MutableStateFlow(false)
    val desktopSite: StateFlow<Boolean> = _desktopSite.asStateFlow()

    private val _canRestoreClosedTab = MutableStateFlow(false)
    val canRestoreClosedTab: StateFlow<Boolean> = _canRestoreClosedTab.asStateFlow()

    /** One-shot message for the UI (string resource id); cleared by [consumeUserMessage]. */
    private val _userMessage = MutableStateFlow<Int?>(null)
    val userMessage: StateFlow<Int?> = _userMessage.asStateFlow()

    /**
     * Incognito tabs run in their own WebView profile (separate cookies, storage, permissions).
     * Without multi-profile support there is no way to isolate them from normal tabs, so
     * incognito is unavailable rather than faked.
     */
    val isIncognitoSupported: Boolean
        get() = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (_: UnsupportedOperationException) {
            false
        }

    init {
        val db = FluxieDatabase.getInstance(application)
        historyRepository = HistoryRepository(db.historyDao())
        bookmarkRepository = BookmarkRepository(db.bookmarkDao())
        downloadRepository = DownloadRepository(db.downloadDao(), AndroidDownloadBackend(application))
        blockedDomainRepository = BlockedDomainRepository(db.blockedDomainDao())
        // Tabs are not persisted, so incognito data left by a killed process is unreachable
        // garbage: remove it before any WebView exists.
        wipeIncognitoData()
        createWebViewForTab(tabManager.activeTab.id, incognito = false)
    }

    fun resolveUrl(input: String): String {
        val url = UrlUtils.resolveInput(input, prefs.searchTemplate())
        // With the adult filter on, searches from the address bar carry the engine's SafeSearch flag.
        return if (blockRules.adultFilterEnabled) SafeSearch.apply(url) else url
    }

    fun getHomepage(): String = prefs.homepage

    /** Loads address-bar input (URL or search terms) in the active tab. Blank input is ignored. */
    fun loadInput(input: String) {
        val url = resolveUrl(input)
        if (url.isEmpty()) return
        getActiveWebView()?.loadUrl(url)
    }

    /** Opens the configured homepage, or the start page when none is set. */
    fun goToHomepage() {
        val homepage = getHomepage().trim()
        if (homepage.isEmpty()) goToStartPage() else loadInput(homepage)
    }

    fun reload() {
        val webView = getActiveWebView() ?: return
        if (webView.url == null) return
        _errorMessage.value = null
        webView.reload()
    }

    fun stopLoading() {
        val webView = getActiveWebView() ?: return
        webView.stopLoading()
        // stopLoading() does not reliably call onPageFinished, so reflect the real state here.
        _loadingProgress.value = -1f
        _canGoBack.value = webView.canGoBack()
        _canGoForward.value = webView.canGoForward()
    }

    /** Switches the active tab between mobile and desktop user agent and reloads the page. */
    fun toggleDesktopSite() {
        val webView = getActiveWebView() ?: return
        val tab = tabManager.activeTab
        tab.desktopSite = !tab.desktopSite
        applyUserAgent(webView, tab.desktopSite)
        _desktopSite.value = tab.desktopSite
        if (webView.url != null && webView.url != "about:blank") {
            _errorMessage.value = null
            webView.reload()
        }
    }

    private fun applyUserAgent(webView: WebView, desktop: Boolean) {
        val default = WebSettings.getDefaultUserAgent(getApplication<Application>())
        webView.settings.apply {
            userAgentString = if (desktop) UserAgentUtils.toDesktop(default) else default
            useWideViewPort = desktop
            loadWithOverviewMode = desktop
        }
    }

    private fun startDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        incognito: Boolean
    ) {
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme != "http" && scheme != "https") {
            // blob:/data: URLs only exist inside the page; DownloadManager cannot fetch them.
            _userMessage.value = R.string.download_unsupported
            return
        }
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val cookie = if (incognito) {
            try {
                ProfileStore.getInstance().getProfile(INCOGNITO_PROFILE)?.cookieManager?.getCookie(url)
            } catch (_: RuntimeException) {
                null
            }
        } else {
            CookieManager.getInstance().getCookie(url)
        }
        viewModelScope.launch {
            val started = downloadRepository.start(
                DownloadRequest(url, fileName, mimeType, userAgent, cookie),
                record = !incognito
            )
            _userMessage.value = if (started) R.string.download_started else R.string.download_failed
        }
    }

    fun consumeUserMessage() {
        _userMessage.value = null
    }

    /** Name shown in the address bar hint; updates the moment the search engine setting changes. */
    private val _searchEngineLabel = MutableStateFlow(prefs.searchDisplayName())
    val searchEngineLabel: StateFlow<String> = _searchEngineLabel.asStateFlow()

    /** Saves the choice; [customTemplate] is stored too when given. Effective immediately. */
    fun setSearchEngine(choice: String, customTemplate: String? = null) {
        customTemplate?.let { prefs.customSearchTemplate = it }
        prefs.searchEngineChoice = choice
        _searchEngineLabel.value = prefs.searchDisplayName()
    }

    // ---- Mode and appearance (DataStore) ----
    val settingsStore = SettingsStore(application)

    /**
     * Current settings. The first value is read synchronously (a tiny file) so the first frame
     * already uses the saved theme instead of flashing the defaults.
     */
    val appSettings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, runBlocking { settingsStore.settings.first() })

    /**
     * Saves the mode in DataStore and in the launch-routing flag. Blocks for the two small writes
     * because the caller replaces the screen right afterwards and the choice must not be lost.
     */
    fun persistMode(mode: BrowserMode) {
        runBlocking { settingsStore.setMode(mode) }
        prefs.liteMode = mode == BrowserMode.LITE
    }
    fun setThemeMode(themeMode: ThemeMode) { viewModelScope.launch { settingsStore.setThemeMode(themeMode) } }
    fun setDynamicColor(enabled: Boolean) { viewModelScope.launch { settingsStore.setDynamicColor(enabled) } }
    fun setAdultFilter(enabled: Boolean) { viewModelScope.launch { settingsStore.setAdultFilter(enabled) } }
    fun setAdBlock(enabled: Boolean) { viewModelScope.launch { settingsStore.setAdBlock(enabled) } }

    // ---- Content filter: rules and request interception ----
    init {
        val adTracker = loadAdTrackerDomains()
        // Loaded synchronously once so the very first page already honours the user's list.
        val stored = runBlocking { blockedDomainRepository.getAll() }
        publishRules(stored, appSettings.value, adTracker)
        viewModelScope.launch {
            combine(blockedDomainRepository.observeAll(), appSettings) { domains, settings -> domains to settings }
                .collect { (domains, settings) -> publishRules(domains, settings, adTracker) }
        }
    }

    // ---- Lite mode hand-over ----
    init {
        viewModelScope.launch {
            // Lite can switch back to Normal without touching DataStore, so bring it in line with the flag.
            val flagMode = if (prefs.liteMode) BrowserMode.LITE else BrowserMode.NORMAL
            if (appSettings.value.mode != flagMode) settingsStore.setMode(flagMode)
            reconcileLiteBookmarks()
        }
    }

    private suspend fun reconcileLiteBookmarks() {
        val sync = LiteBookmarkSync(bookmarkRepository, LiteBookmarkStore(getApplication<Application>()))
        sync.mergeLiteChanges()
        sync.mirror()
    }

    private fun loadAdTrackerDomains(): Set<String> = try {
        val text = getApplication<Application>().assets.open(AD_TRACKER_ASSET)
            .bufferedReader().use { it.readText() }
        BlocklistParser.parse(text).domains.toSet()
    } catch (e: IOException) {
        android.util.Log.w("BrowserViewModel", "Ad/tracker list could not be read; ad blocking is inactive", e)
        emptySet()
    }

    private fun publishRules(domains: List<BlockedDomain>, settings: AppSettings, adTracker: Set<String>) {
        blockRules = BlockRules(
            userDomains = domains.filter { it.category == BlockCategory.GENERAL.name }.map { it.domain }.toSet(),
            adultDomains = domains.filter { it.category == BlockCategory.ADULT.name }.map { it.domain }.toSet(),
            adTrackerDomains = adTracker,
            adultFilterEnabled = settings.adultFilter,
            adBlockEnabled = settings.adBlock
        )
    }

    /**
     * Called on a WebView IO thread for every request. Returns a replacement response when the
     * request is blocked (an explanatory page for the main frame, an empty 403 for sub-resources),
     * or null to let it load normally.
     */
    internal fun interceptRequest(tabId: Int, url: String, isMainFrame: Boolean): WebResourceResponse? {
        val reason = ContentBlocker.decide(url, blockRules, isMainFrame) ?: return null
        val context = getApplication<Application>()
        if (isMainFrame) {
            val host = com.fluxie.browser.common.DomainRules.hostOf(url).orEmpty()
            val reasonLabel = context.getString(
                if (reason == BlockReason.ADULT_LIST) R.string.blocked_reason_adult else R.string.blocked_reason_user
            )
            val html = BlockPage.html(
                host, reasonLabel,
                context.getString(R.string.blocked_page_title),
                context.getString(R.string.blocked_page_detail)
            )
            return WebResourceResponse(
                "text/html", "UTF-8", 403, "Blocked", emptyMap(),
                ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
            )
        }
        val count = blockedCounts.increment(tabId)
        if (_activeTabId.value == tabId) _blockedCount.value = count
        return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    /** With the adult filter on, re-routes a main-frame search navigation through SafeSearch. */
    private fun redirectToSafeSearch(view: WebView?, url: String): Boolean {
        if (!blockRules.adultFilterEnabled || view == null) return false
        val safe = SafeSearch.apply(url)
        if (safe == url) return false
        view.loadUrl(safe)
        return true
    }

    private fun isBlockedPage(url: String): Boolean =
        ContentBlocker.decide(url, blockRules, isMainFrame = true) != null

    // ---- Privacy: clear data (normal profile only; incognito data is wiped on close) ----
    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearAll() }
    }

    fun clearCookiesAndSiteData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    fun clearCache() {
        webViews.values.forEach { it.clearCache(true) }
    }

    /** Clears the selected kinds of browsing data in one go (normal profile only). */
    fun clearBrowsingData(history: Boolean, cache: Boolean, cookiesAndSiteData: Boolean) {
        if (history) clearHistory()
        if (cache) clearCache()
        if (cookiesAndSiteData) clearCookiesAndSiteData()
    }

    fun getActiveWebView(): WebView? = webViews[tabManager.activeTab.id]

    fun getHistoryRepository(): HistoryRepository = historyRepository

    fun getBookmarkRepository(): BookmarkRepository = bookmarkRepository

    fun getDownloadRepository(): DownloadRepository = downloadRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _currentUrl
        .flatMapLatest { url ->
            if (url.isEmpty() || url == "about:blank") {
                flowOf(false)
            } else {
                bookmarkRepository.observeBookmarkByUrl(url).map { it != null }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val allFolders: StateFlow<List<com.fluxie.browser.data.BookmarkEntry>> =
        bookmarkRepository.getAllFolders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Adds bookmark to the last-used folder. Returns the id and folder name via [onAdded]. */
    fun addBookmark(onAdded: (id: Long, folderName: String?) -> Unit = { _, _ -> }) {
        // Incognito pages must never be written to bookmarks.
        if (tabManager.activeTab.isIncognito) return
        val url = _currentUrl.value
        val title = _pageTitle.value
        if (url.isEmpty() || url == "about:blank") return
        val faviconBytes = _favicon.value?.let { bmp ->
            ByteArrayOutputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        }
        val folderId = prefs.lastBookmarkFolderId
        viewModelScope.launch {
            val entry = bookmarkRepository.addBookmark(title, url, faviconBytes, folderId)
            val folderName = folderId?.let { bookmarkRepository.getById(it)?.title }
            onAdded(entry.id, folderName)
        }
    }

    fun moveBookmark(bookmarkId: Long, newParentId: Long?) {
        prefs.lastBookmarkFolderId = newParentId
        viewModelScope.launch {
            bookmarkRepository.moveEntry(bookmarkId, newParentId)
        }
    }

    fun removeBookmarkForCurrentPage() {
        if (tabManager.activeTab.isIncognito) return
        val url = _currentUrl.value
        if (url.isEmpty()) return
        viewModelScope.launch {
            val bookmark = bookmarkRepository.getBookmarkByUrl(url)
            bookmark?.let { bookmarkRepository.deleteEntry(it.id) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebViewForTab(tabId: Int, incognito: Boolean): WebView {
        val vm = this
        return WebView(getApplication<Application>()).apply {
            if (incognito) {
                // Must happen before anything is loaded in this WebView.
                ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
                WebViewCompat.setProfile(this, INCOGNITO_PROFILE)
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                @Suppress("DEPRECATION")
                settings.saveFormData = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.setSupportMultipleWindows(true)

            setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                if (vm.tabManager.activeTab.id == tabId) {
                    vm._findResult.value = FindResult(
                        active = if (numberOfMatches == 0) 0 else activeMatchOrdinal + 1,
                        total = numberOfMatches
                    )
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                vm.startDownload(url, userAgent, contentDisposition, mimeType, incognito)
            }

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                }
            } catch (_: UnsupportedOperationException) {
                // Not supported in this environment
            }

            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    request ?: return null
                    return vm.interceptRequest(tabId, request.url.toString(), request.isForMainFrame)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request != null && request.isForMainFrame && vm.redirectToSafeSearch(view, request.url.toString())

                @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                    url != null && vm.redirectToSafeSearch(view, url)

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    // A new page starts a new blocked-request count.
                    vm.blockedCounts.reset(tabId)
                    if (vm.tabManager.activeTab.id == tabId) vm._blockedCount.value = 0
                    if (vm.tabManager.activeTab.id == tabId) {
                        vm._currentUrl.value = vm.displayUrl(url)
                        vm._loadingProgress.value = 0f
                        vm._errorMessage.value = null
                        vm._canGoBack.value = view?.canGoBack() ?: false
                        vm._canGoForward.value = view?.canGoForward() ?: false
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    // Always update tab metadata
                    val tab = vm.tabManager.tabs.find { it.id == tabId }
                    tab?.url = url ?: ""
                    tab?.title = view?.title ?: ""

                    // Record in history — never for incognito tabs
                    val historyUrl = url ?: ""
                    val historyTitle = view?.title ?: ""
                    if (!incognito && historyUrl.isNotEmpty() && historyUrl != "about:blank" && !vm.isBlockedPage(historyUrl)) {
                        vm.viewModelScope.launch {
                            vm.historyRepository.addEntry(historyUrl, historyTitle)
                        }
                    }

                    // Only update UI state if this is the active tab
                    if (vm.tabManager.activeTab.id == tabId) {
                        vm._currentUrl.value = vm.displayUrl(url)
                        vm._pageTitle.value = if (url == "about:blank") "" else view?.title ?: ""
                        vm._loadingProgress.value = -1f
                        vm._canGoBack.value = view?.canGoBack() ?: false
                        vm._canGoForward.value = view?.canGoForward() ?: false
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    // Fires for every history change, including in-page (pushState) navigation
                    // that never triggers onPageStarted/onPageFinished.
                    if (vm.tabManager.activeTab.id == tabId && view != null) {
                        vm._canGoBack.value = view.canGoBack()
                        vm._canGoForward.value = view.canGoForward()
                        if (url != null && vm._errorMessage.value == null) {
                            vm._currentUrl.value = vm.displayUrl(url)
                        }
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.cancel()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        if (vm.tabManager.activeTab.id == tabId) {
                            vm._loadingProgress.value = -1f
                            vm._errorMessage.value = error?.description?.toString()
                        }
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (vm.tabManager.activeTab.id == tabId) {
                        // 100% means the load is done: hide the bar instead of leaving it full.
                        vm._loadingProgress.value = if (newProgress >= 100) -1f else newProgress / 100f
                    }
                }

                override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                    vm.tabManager.tabs.find { it.id == tabId }?.favicon = icon
                    if (vm.tabManager.activeTab.id == tabId) {
                        vm._favicon.value = icon
                    }
                    view?.url?.let { url ->
                        if (!incognito && icon != null && url.isNotEmpty() && url != "about:blank") {
                            val bytes = ByteArrayOutputStream().use { stream ->
                                icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                stream.toByteArray()
                            }
                            vm.viewModelScope.launch {
                                try {
                                    vm.historyRepository.updateFavicon(url, bytes)
                                } catch (_: Exception) {
                                    // Non-critical: favicon update failure shouldn't crash the app
                                }
                            }
                        }
                    }
                }
            }
        }.also {
            webViews[tabId] = it
        }
    }

    /**
     * about:blank *is* the start page, so it must surface as an empty URL — BrowserScreen
     * gates the New Tab Page on `currentUrl.isEmpty()`.
     */
    private fun displayUrl(url: String?): String = if (url == "about:blank") "" else url ?: ""

    private fun syncUiStateFromWebView(webView: WebView?) {
        _blockedCount.value = blockedCounts.get(tabManager.activeTab.id)
        if (webView != null) {
            val url = webView.url
            _currentUrl.value = displayUrl(url)
            _pageTitle.value = if (url == "about:blank") "" else webView.title ?: ""
            _canGoBack.value = webView.canGoBack()
            _canGoForward.value = webView.canGoForward()
            _loadingProgress.value = -1f
            _favicon.value = webView.favicon
            _errorMessage.value = null
        }
    }

    /**
     * Opens a new tab and makes it active. Returns false (and posts a [userMessage]) if an
     * incognito tab was requested but this WebView cannot isolate one.
     */
    fun addTab(incognito: Boolean = false): Boolean {
        if (incognito && !isIncognitoSupported) {
            _userMessage.value = R.string.incognito_unsupported
            return false
        }
        captureActiveTabThumbnail()
        webViews[tabManager.activeTab.id]?.onPause()

        val tab = tabManager.addTab(incognito)
        createWebViewForTab(tab.id, incognito)
        _activeTabId.value = tab.id
        _tabCount.value = tabManager.tabCount
        _isIncognito.value = incognito
        _desktopSite.value = false
        _currentUrl.value = ""
        _pageTitle.value = ""
        _canGoBack.value = false
        _canGoForward.value = false
        _loadingProgress.value = -1f
        _favicon.value = null
        _errorMessage.value = null
        dismissFindInPage()
        return true
    }

    fun goToStartPage() {
        getActiveWebView()?.loadUrl("about:blank")
        _currentUrl.value = ""
        _pageTitle.value = ""
        _canGoBack.value = false
        _canGoForward.value = false
        _loadingProgress.value = -1f
        _favicon.value = null
        _errorMessage.value = null
        dismissFindInPage()
    }

    fun captureActiveTabThumbnail() {
        val webView = getActiveWebView() ?: return
        if (webView.width <= 0 || webView.height <= 0) return
        val ratio = 360f / webView.width
        tabManager.activeTab.thumbnail =
            createBitmap(360, (webView.height * ratio).toInt().coerceAtLeast(1)).applyCanvas {
                scale(ratio, ratio)
                webView.draw(this)
            }
    }

    fun openTabGrid() {
        captureActiveTabThumbnail()
        _showTabGrid.value = true
    }

    fun dismissTabGrid() {
        _showTabGrid.value = false
    }

    fun switchTab(index: Int) {
        if (index == tabManager.activeTabIndex) return

        // Capture thumbnail before switching away
        captureActiveTabThumbnail()

        // Pause current WebView
        webViews[tabManager.activeTab.id]?.onPause()
        dismissFindInPage()

        tabManager.switchTo(index)
        _activeTabId.value = tabManager.activeTab.id
        _isIncognito.value = tabManager.activeTab.isIncognito
        _desktopSite.value = tabManager.activeTab.desktopSite

        // Resume new WebView and update UI state. A tab released on low memory gets a new WebView here.
        val webView = webViewFor(tabManager.activeTab)
        webView.onResume()
        syncUiStateFromWebView(webView)
    }

    fun closeTab(index: Int) {
        val tabToClose = tabManager.tabs[index]
        val webView = webViews[tabToClose.id]

        // Remember where a normal tab was (page and back/forward list) so it can be reopened.
        var webState: Bundle? = discardedStates[tabToClose.id]
        if (webView != null && !tabToClose.isIncognito) {
            webView.url?.takeIf { it != "about:blank" }?.let { tabToClose.url = it }
            webView.title?.takeIf { it.isNotEmpty() }?.let { tabToClose.title = it }
            webState = Bundle().also { webView.saveState(it) }
        }

        val newTab = tabManager.closeTab(index, webState)

        destroyWebView(tabToClose.id)
        if (tabToClose.isIncognito && tabManager.incognitoTabCount == 0) {
            wipeIncognitoData()
        }

        if (newTab != null) {
            // Last tab was closed, a new blank tab was auto-created
            createWebViewForTab(newTab.id, incognito = false)
        }

        _activeTabId.value = tabManager.activeTab.id
        _tabCount.value = tabManager.tabCount
        _isIncognito.value = tabManager.activeTab.isIncognito
        _desktopSite.value = tabManager.activeTab.desktopSite
        _canRestoreClosedTab.value = tabManager.canRestoreClosedTab
        dismissFindInPage()

        val active = webViews[tabManager.activeTab.id]
        active?.onResume()
        syncUiStateFromWebView(active)
    }

    /** Reopens the most recently closed tab, with its back/forward history when available. */
    fun restoreClosedTab(): Boolean {
        if (!tabManager.canRestoreClosedTab) return false
        captureActiveTabThumbnail()
        webViews[tabManager.activeTab.id]?.onPause()
        dismissFindInPage()

        val restored = tabManager.restoreClosedTab() ?: return false
        restored.replacedTabId?.let { destroyWebView(it) }

        val webView = createWebViewForTab(restored.tab.id, incognito = false)
        val state = restored.closed.webState
        if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(restored.closed.url)
        }

        _activeTabId.value = restored.tab.id
        _tabCount.value = tabManager.tabCount
        _isIncognito.value = false
        _desktopSite.value = false
        _canRestoreClosedTab.value = tabManager.canRestoreClosedTab
        // Show the page being restored right away instead of flashing the start page.
        _currentUrl.value = restored.closed.url
        _pageTitle.value = restored.closed.title
        _canGoBack.value = false
        _canGoForward.value = false
        _loadingProgress.value = 0f
        _favicon.value = null
        _errorMessage.value = null
        return true
    }

    /**
     * The tab's WebView, created (and its page restored) if it was released on low memory. Pages
     * come back from the saved back/forward state, or are reloaded from the tab's URL.
     */
    private fun webViewFor(tab: Tab): WebView {
        webViews[tab.id]?.let { return it }
        val webView = createWebViewForTab(tab.id, tab.isIncognito)
        if (tab.desktopSite) applyUserAgent(webView, true)
        val state = discardedStates.remove(tab.id)
        if (state == null || webView.restoreState(state) == null) {
            if (tab.url.isNotEmpty() && tab.url != "about:blank") webView.loadUrl(tab.url)
        }
        return webView
    }

    /**
     * Called when the system reports memory pressure (see [TrimPolicy]). Frees the WebViews of tabs
     * that are not visible. Incognito tabs are kept: their pages cannot be restored safely.
     * Returns how many tabs were released.
     */
    fun releaseBackgroundTabs(): Int {
        val tabs = tabManager.tabs
        val victims = TrimPolicy.tabsToRelease(
            tabIds = tabs.map { it.id },
            visibleId = tabManager.activeTab.id,
            aliveIds = webViews.keys.toSet(),
            protectedIds = tabs.filter { it.isIncognito }.map { it.id }.toSet()
        )
        for (id in victims) {
            val tab = tabs.firstOrNull { it.id == id } ?: continue
            val webView = webViews[id] ?: continue
            webView.url?.takeIf { it != "about:blank" }?.let { tab.url = it }
            webView.title?.takeIf { it.isNotEmpty() }?.let { tab.title = it }
            discardedStates[id] = Bundle().also { webView.saveState(it) }
            destroyWebView(id)
        }
        return victims.size
    }

    private fun destroyWebView(tabId: Int) {
        blockedCounts.reset(tabId)
        val webView = webViews.remove(tabId) ?: return
        // Detach first: destroying a WebView still parented to the AndroidView container lets a
        // draw pass hit a dead WebView before the swap.
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.clearHistory()
        webView.clearFormData()
        webView.destroy()
    }

    /**
     * Deletes everything the incognito profile stored (cookies, DOM storage, ...). Only call when
     * no incognito WebView is alive. Deleting the profile is the clean way; if the WebView still
     * reports it as in use, cookies and storage are cleared in place instead.
     */
    private fun wipeIncognitoData() {
        if (!isIncognitoSupported) return
        val store = ProfileStore.getInstance()
        val deleted = try {
            store.deleteProfile(INCOGNITO_PROFILE)
        } catch (_: IllegalStateException) {
            false
        }
        if (!deleted) {
            store.getProfile(INCOGNITO_PROFILE)?.let { profile ->
                profile.cookieManager.removeAllCookies(null)
                profile.webStorage.deleteAllData()
            }
        }
    }

    fun findInPage(query: String) {
        if (query.isEmpty()) {
            getActiveWebView()?.clearMatches()
            _findResult.value = FindResult()
        } else {
            getActiveWebView()?.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        getActiveWebView()?.findNext(forward)
    }

    fun showFindInPage() {
        _findInPageActive.value = true
    }

    fun dismissFindInPage() {
        _findInPageActive.value = false
        _findResult.value = FindResult()
        getActiveWebView()?.clearMatches()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        val hadIncognito = tabManager.incognitoTabCount > 0
        webViews.values.forEach { it.destroy() }
        webViews.clear()
        if (hadIncognito) wipeIncognitoData()
        // Lite mode must not keep Room open after this screen is gone; the next Normal start reopens it.
        FluxieDatabase.closeInstance()
    }

    private companion object {
        const val INCOGNITO_PROFILE = "fluxie_incognito"
        const val AD_TRACKER_ASSET = "ad_tracker_domains.txt"
    }
}
