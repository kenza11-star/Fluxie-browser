package com.fluxie.browser.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.fluxie.browser.R
import com.fluxie.browser.data.AddResult
import com.fluxie.browser.data.BlockCategory
import com.fluxie.browser.ui.browser.BrowserViewModel
import com.fluxie.browser.ui.common.BarDestination
import com.fluxie.browser.ui.common.FluxieBottomBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Largest blocklist file the importer reads; bigger files are refused instead of half-read. */
private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

private sealed interface ReadOutcome {
    data class Text(val text: String) : ReadOutcome
    data object TooLarge : ReadOutcome
    data object Failed : ReadOutcome
}

private suspend fun readImportFile(context: Context, uri: Uri): ReadOutcome = withContext(Dispatchers.IO) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
        } ?: return@withContext ReadOutcome.Failed
        if (bytes.size > MAX_IMPORT_BYTES) ReadOutcome.TooLarge else ReadOutcome.Text(String(bytes, Charsets.UTF_8))
    } catch (_: IOException) {
        ReadOutcome.Failed
    } catch (_: SecurityException) {
        ReadOutcome.Failed
    }
}

/** Reads at most [limit] bytes (InputStream.readNBytes needs API 33). */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var remaining = limit
    while (remaining > 0) {
        val n = read(buffer, 0, minOf(buffer.size, remaining))
        if (n < 0) break
        out.write(buffer, 0, n)
        remaining -= n
    }
    return out.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterScreen(navController: NavController, browserViewModel: BrowserViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val repository = browserViewModel.blockedDomainRepository
    val settings by browserViewModel.appSettings.collectAsStateWithLifecycle()

    var category by remember { mutableStateOf(BlockCategory.GENERAL) }
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    val domains by remember(category) { repository.observe(category) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    fun say(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun addTyped() {
        val typed = input
        scope.launch {
            when (val result = repository.add(typed, category)) {
                is AddResult.Added -> {
                    input = ""
                    inputError = false
                    snackbarHostState.showSnackbar(context.getString(R.string.blocklist_added, result.domain))
                }
                is AddResult.AlreadyListed -> {
                    inputError = false
                    snackbarHostState.showSnackbar(context.getString(R.string.blocklist_already, result.domain))
                }
                AddResult.Invalid -> inputError = true
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val outcome = readImportFile(context, uri)) {
                is ReadOutcome.Text -> {
                    val r = repository.importText(outcome.text, category)
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.blocklist_import_result, r.added, r.alreadyListed, r.skippedLines)
                    )
                }
                ReadOutcome.TooLarge -> snackbarHostState.showSnackbar(context.getString(R.string.blocklist_import_too_large))
                ReadOutcome.Failed -> snackbarHostState.showSnackbar(context.getString(R.string.blocklist_import_failed))
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_content_filter)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.menu_back))
                    }
                }
            )
        },
        bottomBar = { FluxieBottomBar(BarDestination.SETTINGS, navController) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSection(stringResource(R.string.settings_content_filter)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.adult_filter)) },
                        supportingContent = { Text(stringResource(R.string.adult_filter_desc)) },
                        trailingContent = {
                            Switch(checked = settings.adultFilter, onCheckedChange = { browserViewModel.setAdultFilter(it) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ad_block)) },
                        supportingContent = { Text(stringResource(R.string.ad_block_desc)) },
                        trailingContent = {
                            Switch(checked = settings.adBlock, onCheckedChange = { browserViewModel.setAdBlock(it) })
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            item {
                SettingsSection(stringResource(R.string.blocklist_general)) {
                    ChoiceRow(
                        options = listOf(
                            stringResource(R.string.blocklist_general) to BlockCategory.GENERAL,
                            stringResource(R.string.blocklist_adult) to BlockCategory.ADULT
                        ),
                        selected = category,
                        onSelect = { category = it; inputError = false }
                    )
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it; inputError = false },
                            placeholder = { Text(stringResource(R.string.blocklist_add_hint)) },
                            singleLine = true,
                            isError = inputError,
                            supportingText = if (inputError) {
                                { Text(stringResource(R.string.blocklist_invalid)) }
                            } else null,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { addTyped() }),
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { addTyped() }, enabled = input.isNotBlank(), modifier = Modifier.padding(top = 6.dp)) {
                            Text(stringResource(R.string.blocklist_add))
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Text(stringResource(R.string.blocklist_import))
                        }
                        if (domains.isNotEmpty()) {
                            TextButton(onClick = { confirmClear = true }) {
                                Text(stringResource(R.string.blocklist_clear_all))
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.blocklist_format_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp)
                    )
                }
            }

            if (domains.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.blocklist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(domains, key = { it.domain }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.domain) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { repository.remove(entry.domain) } }) {
                                Icon(Icons.Default.Close, stringResource(R.string.blocklist_remove))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(if (category == BlockCategory.GENERAL) R.string.blocklist_general else R.string.blocklist_adult)) },
            text = { Text(stringResource(R.string.blocklist_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { repository.clear(category) }
                }) { Text(stringResource(R.string.blocklist_clear_all)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
