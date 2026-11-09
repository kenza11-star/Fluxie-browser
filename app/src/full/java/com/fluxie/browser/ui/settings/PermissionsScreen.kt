package com.fluxie.browser.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.fluxie.browser.R
import com.fluxie.browser.common.PermissionStatus
import com.fluxie.browser.common.locationStatus
import com.fluxie.browser.ui.common.BarDestination
import com.fluxie.browser.ui.common.FluxieBottomBar

private class PermissionRow(
    @StringRes val label: Int,
    val status: PermissionStatus,
    /** Runtime permissions to request, or null when only system settings can change it. */
    val request: Array<String>?
)

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Reads the live state from Android each time; nothing is cached or assumed. */
private fun readPermissionRows(context: Context): List<PermissionRow> {
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PermissionRow(
            R.string.permission_notifications,
            if (granted(context, Manifest.permission.POST_NOTIFICATIONS)) PermissionStatus.ALLOWED else PermissionStatus.DENIED,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        )
    } else {
        // Before Android 13 there is no runtime permission; the user's switch is the truth.
        PermissionRow(
            R.string.permission_notifications,
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) PermissionStatus.ALLOWED else PermissionStatus.DENIED,
            null
        )
    }
    return listOf(
        PermissionRow(
            R.string.permission_camera,
            if (granted(context, Manifest.permission.CAMERA)) PermissionStatus.ALLOWED else PermissionStatus.DENIED,
            arrayOf(Manifest.permission.CAMERA)
        ),
        PermissionRow(
            R.string.permission_microphone,
            if (granted(context, Manifest.permission.RECORD_AUDIO)) PermissionStatus.ALLOWED else PermissionStatus.DENIED,
            arrayOf(Manifest.permission.RECORD_AUDIO)
        ),
        PermissionRow(
            R.string.permission_location,
            locationStatus(
                fineGranted = granted(context, Manifest.permission.ACCESS_FINE_LOCATION),
                coarseGranted = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            ),
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        ),
        notifications
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(navController: NavController) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    // The user may change permissions in system settings and come back: re-read on every resume.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh++
    }

    val rows = remember(refresh) { readPermissionRows(context) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_permissions)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.menu_back))
                    }
                }
            )
        },
        bottomBar = { FluxieBottomBar(BarDestination.SETTINGS, navController) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.permissions_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PermissionRowView(row) {
                            when {
                                row.status == PermissionStatus.ALLOWED -> openAppSettings(context)
                                row.request != null -> launcher.launch(row.request)
                                else -> openNotificationSettings(context)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { openAppSettings(context) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.open_system_settings)) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PermissionRowView(row: PermissionRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(row.label),
            style = MaterialTheme.typography.bodyLarge
        )
        val allowed = row.status == PermissionStatus.ALLOWED
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (allowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (allowed) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                text = stringResource(
                    when (row.status) {
                        PermissionStatus.ALLOWED -> R.string.permission_allowed
                        PermissionStatus.APPROXIMATE -> R.string.permission_approximate
                        PermissionStatus.DENIED -> R.string.permission_not_allowed
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (allowed) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
