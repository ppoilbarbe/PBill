package net.cardolan.pbill.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.cardolan.pbill.R
import net.cardolan.pbill.data.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onBack: () -> Unit) {
    val defaultCurrency by settings.defaultCurrency.collectAsStateWithLifecycle()
    val reopenLastTab by settings.reopenLastTab.collectAsStateWithLifecycle()

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
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
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
        }
    }
}
