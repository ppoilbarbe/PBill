package net.cardolan.pbill.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import net.cardolan.pbill.R
import net.cardolan.pbill.domain.Money

/**
 * Name and currency of a tab, to create one or to edit an existing one.
 *
 * When editing ([hasAmounts] tells whether the tab holds amounts), changing the currency
 * shows that amounts are not converted, and that they get rounded when the new currency
 * has fewer decimals.
 */
@Composable
fun GroupDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialCurrency: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, currency: String) -> Unit,
    hasAmounts: Boolean = false,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }
    val currencyChanged = hasAmounts && currency != initialCurrency
    val rounded = currencyChanged && Money.fractionDigits(currency) < Money.fractionDigits(initialCurrency)
    val modified = name != initialName || currency != initialCurrency
    var confirmDiscard by remember { mutableStateOf(false) }

    if (confirmDiscard) {
        DiscardChangesDialog(onDiscard = onDismiss, onKeepEditing = { confirmDiscard = false })
    }

    AlertDialog(
        // Tapping outside or going back keeps unsaved input unless confirmed; Cancel discards it
        onDismissRequest = { if (modified) confirmDiscard = true else onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                CurrencyDropdown(
                    label = stringResource(R.string.group_currency),
                    selectedCode = currency,
                    onSelect = { currency = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (currencyChanged) {
                    Text(
                        stringResource(R.string.group_currency_not_converted) +
                            if (rounded) " " + stringResource(R.string.group_currency_rounded) else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rounded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), currency) },
                enabled = name.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
