package com.fluxie.browser.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fluxie.browser.ui.about.AboutScreen
import com.fluxie.browser.ui.bookmarks.BookmarkViewModel
import com.fluxie.browser.ui.bookmarks.BookmarksScreen
import com.fluxie.browser.ui.browser.BrowserScreen
import com.fluxie.browser.ui.browser.BrowserViewModel
import com.fluxie.browser.ui.downloads.DownloadsScreen
import com.fluxie.browser.ui.downloads.DownloadsViewModel
import com.fluxie.browser.ui.history.HistoryScreen
import com.fluxie.browser.ui.history.HistoryViewModel
import com.fluxie.browser.ui.language.LanguageScreen
import com.fluxie.browser.ui.settings.ContentFilterScreen
import com.fluxie.browser.ui.settings.PermissionsScreen
import com.fluxie.browser.ui.settings.SettingsScreen

object Routes {
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
    const val LANGUAGE = "language"
    const val ABOUT = "about"
    const val HISTORY = "history"
    const val BOOKMARKS = "bookmarks"
    const val DOWNLOADS = "downloads"
    const val PERMISSIONS = "permissions"
    const val CONTENT_FILTER = "content_filter"
}

@Composable
fun FluxieNavGraph() {
    val navController = rememberNavController()
    // ViewModel scoped to activity so it survives navigation
    val browserViewModel: BrowserViewModel = viewModel()

    val slideDuration = 300

    NavHost(
        navController = navController,
        startDestination = Routes.BROWSER,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(slideDuration))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(slideDuration))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(slideDuration))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(slideDuration))
        }
    ) {
        composable(Routes.BROWSER) {
            BrowserScreen(
                viewModel = browserViewModel,
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onNavigateToDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onNavigateToContentFilter = { navController.navigate(Routes.CONTENT_FILTER) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                navController = navController,
                browserViewModel = browserViewModel
            )
        }
        composable(Routes.CONTENT_FILTER) {
            ContentFilterScreen(navController, browserViewModel)
        }
        composable(Routes.PERMISSIONS) {
            PermissionsScreen(navController)
        }
        composable(Routes.LANGUAGE) {
            LanguageScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            val historyViewModel: HistoryViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { HistoryViewModel(browserViewModel.getHistoryRepository()) }
                }
            )
            HistoryScreen(
                viewModel = historyViewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { url ->
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                    browserViewModel.getActiveWebView()?.loadUrl(url)
                }
            )
        }
        composable(Routes.BOOKMARKS) {
            val bookmarkViewModel: BookmarkViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { BookmarkViewModel(browserViewModel.getBookmarkRepository()) }
                }
            )
            BookmarksScreen(
                viewModel = bookmarkViewModel,
                onBack = { navController.popBackStack() },
                onNavigate = { url ->
                    navController.popBackStack(Routes.BROWSER, inclusive = false)
                    browserViewModel.getActiveWebView()?.loadUrl(url)
                }
            )
        }
        composable(Routes.DOWNLOADS) {
            val downloadsViewModel: DownloadsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { DownloadsViewModel(browserViewModel.getDownloadRepository()) }
                }
            )
            DownloadsScreen(
                viewModel = downloadsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
