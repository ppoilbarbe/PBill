package net.cardolan.pbill.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.cardolan.pbill.R
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.data.Settings
import java.time.LocalDate

/**
 * Backup files go to a shared folder picked by the user (Downloads by default), never to the
 * app's own storage, so that they survive the uninstallation of the app.
 */
private val DOWNLOADS_URI: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download")

private class CreateBackupDocument : ActivityResultContracts.CreateDocument("application/json") {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
}

private class OpenBackupDocument : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DOWNLOADS_URI)
}

/** Types offered when picking a backup to import; some providers report JSON files as text or binary. */
private val BACKUP_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repository: Repository, settings: Settings, onBack: () -> Unit) {
    val defaultCurrency by settings.defaultCurrency.collectAsStateWithLifecycle()
    val reopenLastTab by settings.reopenLastTab.collectAsStateWithLifecycle()
    val viewModel = rememberViewModel { SettingsViewModel(repository) }
    val result by viewModel.result.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var importUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(CreateBackupDocument()) { uri ->
        if (uri != null) viewModel.export(context.contentResolver, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(OpenBackupDocument()) { uri ->
        importUri = uri
    }

    LaunchedEffect(result) {
        val shown = result ?: return@LaunchedEffect
        snackbar.showSnackbar(shown.message(context))
        viewModel.clearResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            CurrencyDropdown(
                label = stringResource(R.string.settings_default_currency),
                selectedCode = defaultCurrency,
                onSelect = settings::setDefaultCurrency,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_default_currency_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_reopen_last)) },
                supportingContent = { Text(stringResource(R.string.settings_reopen_last_help)) },
                leadingContent = { Checkbox(checked = reopenLastTab, onCheckedChange = null) },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .toggleable(value = reopenLastTab, role = Role.Checkbox, onValueChange = settings::setReopenLastTab),
            )
            Text(
                stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_export)) },
                supportingContent = { Text(stringResource(R.string.settings_export_help)) },
                modifier = Modifier.clickable { exportLauncher.launch("pbill-${LocalDate.now()}.json") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_import)) },
                supportingContent = { Text(stringResource(R.string.settings_import_help)) },
                modifier = Modifier.clickable { importLauncher.launch(BACKUP_TYPES) },
            )
        }
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    importUri = null
                    viewModel.import(context.contentResolver, uri)
                }) { Text(stringResource(R.string.settings_import)) }
            },
            dismissButton = {
                TextButton(onClick = { importUri = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
