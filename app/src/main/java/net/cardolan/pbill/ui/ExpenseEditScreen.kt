package net.cardolan.pbill.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.cardolan.pbill.R
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.domain.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    repository: Repository,
    groupId: String,
    expenseId: String?,
    kind: String,
    onDone: () -> Unit,
) {
    val viewModel = rememberViewModel(key = "expense-$groupId-$expenseId-$kind") {
        ExpenseEditViewModel(repository, groupId, expenseId, kind)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.done) { if (state.done) onDone() }

    // Validation errors show in a snackbar, always visible whatever the length of the form.
    // Cleared once the snackbar is gone, so that saving again shows it again; editing a field
    // clears the error, which also hides the snackbar.
    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(error))
        viewModel.clearError()
    }

    // Leaving with unsaved changes (back arrow or system back) asks first
    fun leave() = if (state.isModified) confirmLeave = true else onDone()
    BackHandler(enabled = state.isModified) { confirmLeave = true }

    val isTransfer = state.kind == ExpenseKind.TRANSFER
    val titleRes = when {
        isTransfer && state.isNew -> R.string.transfer_new_title
        isTransfer -> R.string.transfer_edit_title
        state.isNew -> R.string.expense_new_title
        else -> R.string.expense_edit_title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleRes)) },
                navigationIcon = {
                    // Closes directly when nothing changed, asks first otherwise
                    IconButton(onClick = ::leave) {
                        Icon(Icons.Default.Close, stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
                        }
                    }
                    // An unchanged existing entry just closes, like ✗; otherwise saves (an
                    // incomplete entry shows what is missing)
                    IconButton(onClick = { if (state.isNew || state.isModified) viewModel.save() else onDone() }) {
                        Icon(Icons.Default.Check, stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isTransfer) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text(stringResource(R.string.expense_title)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryDropdown(
                    label = stringResource(R.string.expense_category),
                    selected = state.category,
                    onSelect = viewModel::setCategory,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(R.string.expense_amount)) },
                suffix = { Text(state.currency) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            MemberDropdown(
                label = stringResource(if (isTransfer) R.string.transfer_from else R.string.expense_paid_by),
                members = state.members,
                selectedId = state.payerId,
                onSelect = viewModel::setPayer,
                modifier = Modifier.fillMaxWidth(),
            )
            if (isTransfer) {
                MemberDropdown(
                    label = stringResource(R.string.transfer_to),
                    members = state.members,
                    selectedId = state.recipientId,
                    onSelect = viewModel::setRecipient,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Text(
                    stringResource(R.string.expense_date, Dates.format(state.date)),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (!isTransfer) ParticipantsEditor(state, viewModel)
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(viewModel::setDate)
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(pickerState) }
    }

    if (confirmLeave) {
        DiscardChangesDialog(
            onDiscard = {
                confirmLeave = false
                onDone()
            },
            onKeepEditing = { confirmLeave = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.expense_delete_title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ParticipantsEditor(state: ExpenseEditState, viewModel: ExpenseEditViewModel) {
    val preview = state.sharePreview
    Text(
        stringResource(R.string.expense_split_header),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        stringResource(R.string.expense_split_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.participants.forEach { participant ->
        val id = participant.member.id
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = participant.included, onCheckedChange = { viewModel.setIncluded(id, it) })
            Text(participant.member.name, modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = participant.weightText,
                onValueChange = { viewModel.setWeight(id, it) },
                enabled = participant.included,
                singleLine = true,
                label = { Text(stringResource(R.string.member_weight_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(96.dp),
            )
            Text(
                preview[id]?.let { Money.format(it, state.currency) }.orEmpty(),
                textAlign = TextAlign.End,
                modifier = Modifier.width(96.dp),
            )
        }
    }
}
