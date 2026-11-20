package net.cardolan.pbill.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.cardolan.pbill.R
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.data.MemberEntity
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.domain.Money
import net.cardolan.pbill.domain.Weight

private const val TAB_EXPENSES = 0
private const val TAB_BALANCES = 1
private const val TAB_MEMBERS = 2

/** Member dialog target: null member means a new one. */
private data class MemberDialogTarget(val member: MemberEntity?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    repository: Repository,
    groupId: String,
    onBack: () -> Unit,
    onEditExpense: (expenseId: String?, kind: String) -> Unit,
) {
    val viewModel = rememberViewModel(key = "group-$groupId") { GroupViewModel(repository, groupId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_EXPENSES) }
    var memberDialog by remember { mutableStateOf<MemberDialogTarget?>(null) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The group disappears once deleted
    LaunchedEffect(state.loaded, state.group) {
        if (state.loaded && state.group == null) onBack()
    }
    val group = state.group ?: return
    val currency = group.currency

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    fun exportThen(action: (Context, Uri) -> Boolean) {
                        viewModel.exportPdf(context) { uri ->
                            val error = when {
                                uri == null -> R.string.pdf_error
                                !action(context, uri) -> R.string.pdf_no_viewer
                                else -> null
                            }
                            if (error != null) scope.launch { snackbar.showSnackbar(context.getString(error)) }
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                exportThen(::openPdf)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                exportThen(::sharePdf)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete_group)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                confirmDeleteGroup = true
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            when (tab) {
                TAB_EXPENSES -> ExtendedFloatingActionButton(
                    onClick = {
                        if (state.members.isEmpty()) {
                            tab = TAB_MEMBERS
                            memberDialog = MemberDialogTarget(null)
                        } else {
                            onEditExpense(null, ExpenseKind.EXPENSE)
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_expense)) },
                )
                TAB_BALANCES -> if (state.members.size >= 2) {
                    ExtendedFloatingActionButton(
                        onClick = { onEditExpense(null, ExpenseKind.TRANSFER) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_record_transfer)) },
                    )
                }
                TAB_MEMBERS -> ExtendedFloatingActionButton(
                    onClick = { memberDialog = MemberDialogTarget(null) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add_member)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf(R.string.tab_expenses, R.string.tab_balances, R.string.tab_members).forEachIndexed { i, res ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(res)) })
                }
            }
            when (tab) {
                TAB_EXPENSES -> ExpensesTab(state, currency) { onEditExpense(it.expense.id, it.expense.kind) }
                TAB_BALANCES -> BalancesTab(state, currency, viewModel::recordSettlement)
                TAB_MEMBERS -> MembersTab(state) { memberDialog = MemberDialogTarget(it) }
            }
        }
    }

    memberDialog?.let { target ->
        MemberDialog(
            member = target.member,
            onDismiss = { memberDialog = null },
            onSave = { name, weight ->
                viewModel.saveMember(target.member, name, weight)
                memberDialog = null
            },
            onDelete = { member ->
                memberDialog = null
                viewModel.deleteMember(member) { deleted ->
                    if (!deleted) scope.launch { snackbar.showSnackbar(context.getString(R.string.member_in_use)) }
                }
            },
        )
    }

    if (confirmDeleteGroup) {
        AlertDialog(
            onDismissRequest = { confirmDeleteGroup = false },
            title = { Text(stringResource(R.string.group_delete_title)) },
            text = { Text(stringResource(R.string.group_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteGroup = false
                    viewModel.deleteGroup()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteGroup = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun ExpensesTab(state: GroupUiState, currency: String, onClick: (ExpenseRow) -> Unit) {
    if (state.members.isEmpty()) {
        EmptyHint(stringResource(R.string.members_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.total_spent, Money.format(state.totalSpent, currency)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        if (state.expenses.isEmpty()) {
            item { EmptyHint(stringResource(R.string.expenses_empty)) }
        }
        items(state.expenses, key = { it.expense.id }) { row ->
            val isTransfer = row.expense.kind == ExpenseKind.TRANSFER
            val date = Dates.format(row.expense.date)
            ListItem(
                leadingContent = {
                    Icon(
                        if (isTransfer) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.ShoppingCart,
                        contentDescription = null,
                    )
                },
                headlineContent = {
                    Text(
                        if (isTransfer) {
                            stringResource(R.string.transfer_label, row.payerName, row.recipientName.orEmpty())
                        } else {
                            row.expense.title
                        },
                    )
                },
                supportingContent = {
                    Text(if (isTransfer) date else stringResource(R.string.paid_by, row.payerName, date))
                },
                trailingContent = { Text(Money.format(row.expense.amountMinor, currency)) },
                modifier = Modifier.clickable { onClick(row) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun BalancesTab(state: GroupUiState, currency: String, onRecord: (SettlementRow) -> Unit) {
    if (state.members.isEmpty()) {
        EmptyHint(stringResource(R.string.members_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.balances_header)) }
        items(state.balances, key = { it.member.id }) { row ->
            val color = when {
                row.balance > 0 -> MaterialTheme.colorScheme.primary
                row.balance < 0 -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            ListItem(
                headlineContent = { Text(row.member.name) },
                supportingContent = { Text(stringResource(R.string.member_weight, Weight.format(row.member.weight))) },
                trailingContent = {
                    Text(Money.format(row.balance, currency), color = color, fontWeight = FontWeight.SemiBold)
                },
            )
        }
        item { SectionHeader(stringResource(R.string.settlements_header)) }
        if (state.settlements.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settlements_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.settlements, key = { "${it.from.id}>${it.to.id}" }) { settlement ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.transfer_label, settlement.from.name, settlement.to.name)) },
                supportingContent = { Text(Money.format(settlement.amount, currency)) },
                trailingContent = {
                    TextButton(onClick = { onRecord(settlement) }) { Text(stringResource(R.string.action_mark_paid)) }
                },
            )
        }
    }
}

@Composable
private fun MembersTab(state: GroupUiState, onClick: (MemberEntity) -> Unit) {
    if (state.members.isEmpty()) {
        EmptyHint(stringResource(R.string.members_empty))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            SectionHeader(stringResource(R.string.member_total_weight, Weight.format(state.members.sumOf { it.weight })))
        }
        items(state.members, key = { it.id }) { member ->
            ListItem(
                leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                headlineContent = { Text(member.name) },
                supportingContent = { Text(stringResource(R.string.member_weight, Weight.format(member.weight))) },
                modifier = Modifier.clickable { onClick(member) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MemberDialog(
    member: MemberEntity?,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit,
    onDelete: (MemberEntity) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(member?.name.orEmpty()) }
    var weightText by rememberSaveable { mutableStateOf(Weight.format(member?.weight ?: Weight.ONE)) }
    val weight = Weight.parse(weightText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (member == null) R.string.member_new_title else R.string.member_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.member_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.member_weight_label)) },
                    singleLine = true,
                    isError = weight == null,
                    supportingText = {
                        Text(stringResource(if (weight == null) R.string.error_weight else R.string.member_weight_help))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), weight!!) },
                enabled = name.isNotBlank() && weight != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member != null) {
                    TextButton(onClick = { onDelete(member) }) { Text(stringResource(R.string.action_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

/** Opens a generated PDF report in a viewer app; false when none is installed. */
private fun openPdf(context: Context, uri: Uri): Boolean {
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return try {
        context.startActivity(view)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Opens the standard Android share sheet for a generated PDF report. */
private fun sharePdf(context: Context, uri: Uri): Boolean {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        // ClipData lets the chooser preview the file and carries the read grant
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.pdf_share_title)))
    return true
}
