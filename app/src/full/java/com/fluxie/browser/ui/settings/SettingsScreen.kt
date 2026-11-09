package com.fluxie.browser.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fluxie.browser.R
import com.fluxie.browser.common.BrowserMode
import com.fluxie.browser.common.CUSTOM_SEARCH
import com.fluxie.browser.common.SearchEngine
import com.fluxie.browser.common.SearchTemplate
import com.fluxie.browser.common.ThemeMode
import com.fluxie.browser.common.UrlUtils
import com.fluxie.browser.lite.ModeSwitch
import com.fluxie.browser.ui.browser.BrowserViewModel
import com.fluxie.browser.ui.common.BarDestination
import com.fluxie.browser.ui.common.FluxieBottomBar
import com.fluxie.browser.ui.navigation.Routes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    browserViewModel: BrowserViewModel
) {
    val prefs = browserViewModel.prefs
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by browserViewModel.appSettings.collectAsStateWithLifecycle()
    val engineLabel by browserViewModel.searchEngineLabel.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var showSearchEngineSheet by remember { mutableStateOf(false) }
    var showHomepageSheet by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    // The homepage lives in SharedPreferences (not observable), so bump this to re-read it.
    var homepageVersion by remember { mutableIntStateOf(0) }

    val homepageSubtitle = remember(homepageVersion) {
        if (prefs.homeButtonGoesToStartPage) null else prefs.homepage
    } ?: stringResource(R.string.homepage_start_page)

    // Section titles and the words that make a section match the settings search.
    val modeTitle = stringResource(R.string.settings_mode)
    val appearanceTitle = stringResource(R.string.settings_appearance)
    val searchTitle = stringResource(R.string.settings_search_engine)
    val homepageTitle = stringResource(R.string.settings_homepage)
    val privacyTitle = stringResource(R.string.settings_privacy)
    val permissionsTitle = stringResource(R.string.settings_permissions)
    val advancedTitle = stringResource(R.string.settings_advanced)
    val aboutTitle = stringResource(R.string.settings_about)
    val languageTitle = stringResource(R.string.settings_language)
    val clearDataTitle = stringResource(R.string.clear_browsing_data)
    val contentFilterTitle = stringResource(R.string.settings_content_filter)
    val contentFilterSummary = stringResource(R.string.content_filter_summary)
    val appInfoTitle = stringResource(R.string.app_info)
    val clearDataDone = stringResource(R.string.clear_browsing_data_done)

    fun matches(vararg texts: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || texts.any { it.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.menu_back)
                        )
                    }
                }
            )
        },
        bottomBar = { FluxieBottomBar(BarDestination.SETTINGS, navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsSearchPill(query = query, onQueryChange = { query = it })
            Spacer(Modifier.height(16.dp))

            if (matches(modeTitle, stringResource(R.string.mode_normal), stringResource(R.string.mode_lite))) {
                SettingsSection(modeTitle) {
                    ChoiceRow(
                        options = listOf(
                            stringResource(R.string.mode_normal) to BrowserMode.NORMAL,
                            stringResource(R.string.mode_lite) to BrowserMode.LITE
                        ),
                        selected = settings.mode,
                        onSelect = { mode ->
                            if (mode != settings.mode) {
                                browserViewModel.persistMode(mode)
                                // Lite is a separate, lightweight screen; Normal is this one.
                                if (mode == BrowserMode.LITE) context.findActivity()?.let { ModeSwitch.openLite(it) }
                            }
                        }
                    )
                    Text(
                        text = stringResource(
                            if (settings.mode == BrowserMode.LITE) R.string.mode_lite_desc else R.string.mode_normal_desc
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    )
                }
            }

            if (matches(appearanceTitle, stringResource(R.string.theme_dark), stringResource(R.string.theme_light), stringResource(R.string.dynamic_color))) {
                SettingsSection(appearanceTitle) {
                    ChoiceRow(
                        options = listOf(
                            stringResource(R.string.theme_system) to ThemeMode.SYSTEM,
                            stringResource(R.string.theme_light) to ThemeMode.LIGHT,
                            stringResource(R.string.theme_dark) to ThemeMode.DARK
                        ),
                        selected = settings.themeMode,
                        onSelect = { browserViewModel.setThemeMode(it) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (dynamicSupported) R.string.dynamic_color_desc else R.string.dynamic_color_unavailable
                                )
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor && dynamicSupported,
                                onCheckedChange = { browserViewModel.setDynamicColor(it) },
                                enabled = dynamicSupported
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            if (matches(searchTitle, engineLabel)) {
                SettingsSection(searchTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Search),
                        title = searchTitle,
                        subtitle = engineLabel,
                        onClick = { showSearchEngineSheet = true }
                    )
                }
            }

            if (matches(homepageTitle, homepageSubtitle)) {
                SettingsSection(homepageTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Home),
                        title = homepageTitle,
                        subtitle = homepageSubtitle,
                        onClick = { showHomepageSheet = true }
                    )
                }
            }

            if (matches(privacyTitle, contentFilterTitle, contentFilterSummary, clearDataTitle)) {
                SettingsSection(privacyTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Lock),
                        title = contentFilterTitle,
                        subtitle = contentFilterSummary,
                        onClick = { navController.navigate(Routes.CONTENT_FILTER) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Delete),
                        title = clearDataTitle,
                        onClick = { showClearDialog = true }
                    )
                }
            }

            if (matches(permissionsTitle, stringResource(R.string.settings_permissions_summary))) {
                SettingsSection(permissionsTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Lock),
                        title = permissionsTitle,
                        subtitle = stringResource(R.string.settings_permissions_summary),
                        onClick = { navController.navigate(Routes.PERMISSIONS) }
                    )
                }
            }

            if (matches(advancedTitle, languageTitle, appInfoTitle)) {
                SettingsSection(advancedTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Info),
                        title = languageTitle,
                        onClick = { navController.navigate(Routes.LANGUAGE) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Settings),
                        title = appInfoTitle,
                        subtitle = stringResource(R.string.app_info_summary),
                        onClick = { openAppSettings(context) }
                    )
                }
            }

            if (matches(aboutTitle)) {
                SettingsSection(aboutTitle) {
                    SettingsRow(
                        icon = rememberVectorPainter(Icons.Default.Info),
                        title = aboutTitle,
                        onClick = { navController.navigate(Routes.ABOUT) }
                    )
                }
            }
        }
    }

    // ---- Clear browsing data: choose what to delete, then confirm ----
    if (showClearDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        var clearCookies by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(clearDataTitle) },
            text = {
                Column {
                    Text(stringResource(R.string.clear_browsing_data_message))
                    Spacer(Modifier.height(8.dp))
                    ClearOptionRow(stringResource(R.string.clear_option_history), clearHistory) { clearHistory = it }
                    ClearOptionRow(stringResource(R.string.clear_option_cache), clearCache) { clearCache = it }
                    ClearOptionRow(stringResource(R.string.clear_option_cookies), clearCookies) { clearCookies = it }
                    if (clearCookies) {
                        Text(
                            text = stringResource(R.string.clear_cookies_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearHistory || clearCache || clearCookies,
                    onClick = {
                        browserViewModel.clearBrowsingData(clearHistory, clearCache, clearCookies)
                        showClearDialog = false
                        scope.launch { snackbarHostState.showSnackbar(clearDataDone) }
                    }
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // ---- Search engine sheet ----
    if (showSearchEngineSheet) {
        var choice by remember { mutableStateOf(prefs.searchEngineChoice) }
        var template by remember { mutableStateOf(prefs.customSearchTemplate) }
        val customInvalid = choice == CUSTOM_SEARCH && !SearchTemplate.isValid(template)

        ModalBottomSheet(
            onDismissRequest = { showSearchEngineSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.select_search_engine),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                SearchEngine.entries.forEach { engine ->
                    ListItem(
                        headlineContent = { Text(engine.displayName) },
                        leadingContent = {
                            RadioButton(selected = choice == engine.name, onClick = { choice = engine.name })
                        },
                        modifier = Modifier.clickable { choice = engine.name }
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.search_engine_custom)) },
                    leadingContent = {
                        RadioButton(selected = choice == CUSTOM_SEARCH, onClick = { choice = CUSTOM_SEARCH })
                    },
                    modifier = Modifier.clickable { choice = CUSTOM_SEARCH }
                )
                if (choice == CUSTOM_SEARCH) {
                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        label = { Text(stringResource(R.string.custom_search_template)) },
                        placeholder = { Text("https://example.com/search?q=%s") },
                        singleLine = true,
                        isError = customInvalid && template.isNotBlank(),
                        supportingText = {
                            Text(
                                stringResource(
                                    if (customInvalid && template.isNotBlank()) R.string.custom_search_invalid
                                    else R.string.custom_search_hint
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    enabled = !customInvalid,
                    onClick = {
                        browserViewModel.setSearchEngine(
                            choice = choice,
                            customTemplate = if (choice == CUSTOM_SEARCH) template.trim() else null
                        )
                        showSearchEngineSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.ok))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ---- Homepage sheet ----
    if (showHomepageSheet) {
        var useStartPage by remember { mutableStateOf(prefs.homeButtonGoesToStartPage) }
        var homepageInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showHomepageSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = stringResource(R.string.homepage),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.homepage_start_page)) },
                    leadingContent = {
                        RadioButton(selected = useStartPage, onClick = { useStartPage = true })
                    },
                    modifier = Modifier.clickable { useStartPage = true }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.homepage_custom_url)) },
                    leadingContent = {
                        RadioButton(selected = !useStartPage, onClick = { useStartPage = false })
                    },
                    modifier = Modifier.clickable { useStartPage = false }
                )
                if (!useStartPage) {
                    OutlinedTextField(
                        value = homepageInput,
                        onValueChange = {
                            homepageInput = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.input_new_homepage_url_below)) },
                        placeholder = { Text(prefs.homepage) },
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        // Empty input in custom mode keeps the previously saved URL.
                        val typed = homepageInput.trim().takeIf { !useStartPage && it.isNotEmpty() }
                        val normalized = typed?.let { UrlUtils.asUrlOrNull(it) }
                        if (typed != null && normalized == null) {
                            errorMessage = context.getString(R.string.homepage_edit_error_invalid)
                        } else {
                            normalized?.let { prefs.homepage = it }
                            prefs.homeButtonGoesToStartPage = useStartPage
                            homepageVersion++
                            showHomepageSheet = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ok))
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Centered rounded search pill that filters the settings sections below it. */
@Composable
private fun SettingsSearchPill(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Box(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_hint),
                        style = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** A titled, rounded card holding one group of settings. */
@Composable
internal fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

/** Equal-width rounded choices (Normal/Lite, System/Light/Dark). The selected one is filled. */
@Composable
internal fun <T> ChoiceRow(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 14.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: Painter,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        leadingContent = {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ClearOptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.size(12.dp))
        Text(label)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
