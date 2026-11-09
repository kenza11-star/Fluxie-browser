package com.fluxie.browser.ui.browser

import android.webkit.WebBackForwardList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fluxie.browser.R

/**
 * The browser's main menu: a rounded Compose surface over a dimmed scrim, anchored above the
 * bottom bar. It is drawn inside the screen's own layout (no PopupMenu / DropdownMenu window).
 * Long-pressing Back or Forward swaps the body for that direction's page list.
 */
@Composable
fun BrowserMenu(
    canGoBack: Boolean,
    canGoForward: Boolean,
    backForwardList: WebBackForwardList?,
    isBookmarked: Boolean,
    bookmarkEnabled: Boolean,
    desktopSite: Boolean,
    pageActionsEnabled: Boolean,
    onDismiss: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoToHistoryIndex: (Int) -> Unit,
    onGoHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    blockedCount: Int,
    onContentFilter: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onSettings: () -> Unit
) {
    /** null = main menu, true = list of pages behind the current one, false = pages ahead. */
    var historyDirection by remember { mutableStateOf<Boolean?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 8.dp, bottom = 72.dp)
                .width(300.dp)
                .heightIn(max = 560.dp)
                // Swallow taps so touching the menu itself does not hit the scrim and dismiss it.
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                val direction = historyDirection
                if (direction == null) {
                    MainMenuBody(
                        canGoBack = canGoBack,
                        canGoForward = canGoForward,
                        isBookmarked = isBookmarked,
                        bookmarkEnabled = bookmarkEnabled,
                        desktopSite = desktopSite,
                        pageActionsEnabled = pageActionsEnabled,
                        onShowHistory = { historyDirection = it },
                        onGoBack = onGoBack,
                        onGoForward = onGoForward,
                        onGoHome = onGoHome,
                        onToggleBookmark = onToggleBookmark,
                        onBookmarks = onBookmarks,
                        onHistory = onHistory,
                        onDownloads = onDownloads,
                        blockedCount = blockedCount,
                        onContentFilter = onContentFilter,
                        onFindInPage = onFindInPage,
                        onShare = onShare,
                        onToggleDesktopSite = onToggleDesktopSite,
                        onSettings = onSettings
                    )
                } else {
                    PageListBody(
                        isBack = direction,
                        backForwardList = backForwardList,
                        onBack = { historyDirection = null },
                        onPick = onGoToHistoryIndex
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainMenuBody(
    canGoBack: Boolean,
    canGoForward: Boolean,
    isBookmarked: Boolean,
    bookmarkEnabled: Boolean,
    desktopSite: Boolean,
    pageActionsEnabled: Boolean,
    onShowHistory: (isBack: Boolean) -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    blockedCount: Int,
    onContentFilter: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onSettings: () -> Unit
) {
    // Quick actions
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        MenuTile(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowBack),
            label = stringResource(R.string.menu_back),
            enabled = canGoBack,
            onClick = onGoBack,
            onLongClick = { onShowHistory(true) },
            modifier = Modifier.weight(1f)
        )
        MenuTile(
            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.ArrowForward),
            label = stringResource(R.string.menu_forward),
            enabled = canGoForward,
            onClick = onGoForward,
            onLongClick = { onShowHistory(false) },
            modifier = Modifier.weight(1f)
        )
        MenuTile(
            icon = rememberVectorPainter(Icons.Default.Home),
            label = stringResource(R.string.menu_home),
            enabled = true,
            onClick = onGoHome,
            modifier = Modifier.weight(1f)
        )
        MenuTile(
            icon = painterResource(
                if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_border
            ),
            label = stringResource(R.string.bookmarks),
            enabled = bookmarkEnabled,
            onClick = onToggleBookmark,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.size(12.dp))
    MenuGroup {
        MenuRow(painterResource(R.drawable.ic_bookmark_border), stringResource(R.string.bookmarks), onClick = onBookmarks)
        MenuDivider()
        MenuRow(painterResource(R.drawable.ic_history), stringResource(R.string.history), onClick = onHistory)
        MenuDivider()
        MenuRow(painterResource(R.drawable.ic_download), stringResource(R.string.downloads), onClick = onDownloads)
    }

    Spacer(Modifier.size(8.dp))
    MenuGroup {
        MenuRow(
            painterResource(R.drawable.ic_find_in_page),
            stringResource(R.string.find_in_page),
            enabled = pageActionsEnabled,
            onClick = onFindInPage
        )
        MenuDivider()
        MenuRow(
            rememberVectorPainter(Icons.Default.Share),
            stringResource(R.string.share_page),
            enabled = pageActionsEnabled,
            onClick = onShare
        )
        MenuDivider()
        MenuRow(
            painterResource(R.drawable.ic_desktop),
            stringResource(R.string.desktop_site),
            enabled = pageActionsEnabled,
            onClick = onToggleDesktopSite,
            trailing = {
                // The row itself handles the tap; the switch only mirrors the state.
                Switch(checked = desktopSite, onCheckedChange = null, enabled = pageActionsEnabled)
            }
        )
    }

    Spacer(Modifier.size(8.dp))
    MenuGroup {
        MenuRow(
            rememberVectorPainter(Icons.Default.Lock),
            stringResource(R.string.blocked_on_page),
            onClick = onContentFilter,
            trailing = { Text(blockedCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
        MenuDivider()
        MenuRow(rememberVectorPainter(Icons.Default.Settings), stringResource(R.string.settings), onClick = onSettings)
    }
}

@Composable
private fun PageListBody(
    isBack: Boolean,
    backForwardList: WebBackForwardList?,
    onBack: () -> Unit,
    onPick: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onBack)
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.menu_back),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = stringResource(if (isBack) R.string.menu_back else R.string.menu_forward),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
    Spacer(Modifier.size(4.dp))

    val pages = backForwardList?.let { list ->
        val current = list.currentIndex
        val indices = if (isBack) (current - 1 downTo 0) else ((current + 1) until list.size)
        indices.map { index -> list.getItemAtIndex(index).let { Triple(index, it.title, it.url) } }
    }.orEmpty()

    MenuGroup {
        pages.forEachIndexed { position, (index, title, url) ->
            if (position > 0) MenuDivider()
            Text(
                text = title?.takeIf { it.isNotBlank() } ?: url,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(index) }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuTile(
    icon: Painter,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 52.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun MenuRow(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (enabled) 1f else 0.38f)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)
        )
        trailing?.invoke()
    }
}
