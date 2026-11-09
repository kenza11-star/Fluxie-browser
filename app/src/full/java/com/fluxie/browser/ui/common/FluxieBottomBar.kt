package com.fluxie.browser.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.fluxie.browser.R
import com.fluxie.browser.ui.navigation.Routes

enum class BarDestination { BROWSER, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS }

/**
 * Rounded bottom navigation bar shared by the secondary screens, so Browser, Bookmarks, History,
 * Downloads and Settings are one tap apart. [selected] is highlighted.
 */
@Composable
fun FluxieBottomBar(selected: BarDestination, navController: NavController) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            BarItem(rememberVectorPainter(Icons.Default.Home), stringResource(R.string.nav_browser), selected == BarDestination.BROWSER) {
                navController.popBackStack(Routes.BROWSER, inclusive = false)
            }
            BarItem(painterResource(R.drawable.ic_bookmark_border), stringResource(R.string.bookmarks), selected == BarDestination.BOOKMARKS) {
                navigate(navController, selected, BarDestination.BOOKMARKS, Routes.BOOKMARKS)
            }
            BarItem(painterResource(R.drawable.ic_history), stringResource(R.string.history), selected == BarDestination.HISTORY) {
                navigate(navController, selected, BarDestination.HISTORY, Routes.HISTORY)
            }
            BarItem(painterResource(R.drawable.ic_download), stringResource(R.string.downloads), selected == BarDestination.DOWNLOADS) {
                navigate(navController, selected, BarDestination.DOWNLOADS, Routes.DOWNLOADS)
            }
            BarItem(rememberVectorPainter(Icons.Default.Settings), stringResource(R.string.settings), selected == BarDestination.SETTINGS) {
                navigate(navController, selected, BarDestination.SETTINGS, Routes.SETTINGS)
            }
        }
    }
}

private fun navigate(navController: NavController, from: BarDestination, to: BarDestination, route: String) {
    if (from == to) return
    // Hop sideways between the secondary screens instead of stacking them on each other.
    navController.navigate(route) {
        popUpTo(Routes.BROWSER) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
private fun BarItem(icon: Painter, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(56.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
