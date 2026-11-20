package net.cardolan.pbill.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.cardolan.pbill.data.ExpenseEntity
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.data.GroupEntity
import net.cardolan.pbill.data.MemberEntity
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.data.ShareEntity
import net.cardolan.pbill.domain.Balances
import net.cardolan.pbill.domain.Entry
import net.cardolan.pbill.domain.MemberSummary
import net.cardolan.pbill.domain.Weight
import net.cardolan.pbill.report.PdfReport

data class ExpenseRow(
    val expense: ExpenseEntity,
    val payerName: String,
    /** Recipient of a transfer, null for expenses. */
    val recipientName: String?,
    /** Participants with their weight for this entry. */
    val participants: List<Pair<MemberEntity, Int>>,
)

data class BalanceRow(val member: MemberEntity, val summary: MemberSummary) {
    val balance: Long get() = summary.balance
}

data class SettlementRow(val from: MemberEntity, val to: MemberEntity, val amount: Long)

data class GroupUiState(
    val loaded: Boolean = false,
    val group: GroupEntity? = null,
    val members: List<MemberEntity> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
    val totalSpent: Long = 0,
    val balances: List<BalanceRow> = emptyList(),
    val settlements: List<SettlementRow> = emptyList(),
)

private const val TAG = "GroupViewModel"

class GroupViewModel(private val repository: Repository, private val groupId: String) : ViewModel() {

    val state: StateFlow<GroupUiState> = combine(
        repository.group(groupId),
        repository.members(groupId),
        repository.expenses(groupId),
        repository.shares(groupId),
        ::buildState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupUiState())

    private fun buildState(
        group: GroupEntity?,
        members: List<MemberEntity>,
        expenses: List<ExpenseEntity>,
        shares: List<ShareEntity>,
    ): GroupUiState {
        val byId = members.associateBy { it.id }
        val sharesByExpense = shares.groupBy { it.expenseId }
        val entries = expenses.map { expense ->
            Entry(
                payerId = expense.payerId,
                amount = expense.amountMinor,
                shares = sharesByExpense[expense.id].orEmpty().associate { it.memberId to it.weight },
                isTransfer = expense.kind == ExpenseKind.TRANSFER,
            )
        }
        val summaries = Balances.summarize(members.map { it.id }, entries)
        val balances = summaries.mapValues { it.value.balance }
        val rows = expenses.map { expense ->
            val recipient = if (expense.kind == ExpenseKind.TRANSFER) {
                sharesByExpense[expense.id]?.firstOrNull()?.let { byId[it.memberId]?.name }
            } else {
                null
            }
            val participants = sharesByExpense[expense.id].orEmpty()
                .mapNotNull { share -> byId[share.memberId]?.let { it to share.weight } }
                .sortedBy { (member, _) -> member.createdAt }
            ExpenseRow(expense, byId[expense.payerId]?.name.orEmpty(), recipient, participants)
        }
        return GroupUiState(
            loaded = true,
            group = group,
            members = members,
            expenses = rows,
            totalSpent = expenses.filter { it.kind == ExpenseKind.EXPENSE }.sumOf { it.amountMinor },
            balances = members.map { BalanceRow(it, summaries[it.id] ?: MemberSummary()) },
            settlements = Balances.settle(balances).mapNotNull { s ->
                val from = byId[s.fromId] ?: return@mapNotNull null
                val to = byId[s.toId] ?: return@mapNotNull null
                SettlementRow(from, to, s.amount)
            },
        )
    }

    fun saveMember(existing: MemberEntity?, name: String, weight: Int) {
        viewModelScope.launch { repository.saveMember(groupId, existing, name, weight) }
    }

    fun deleteMember(member: MemberEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.deleteMember(member.id)) }
    }

    /** Records a suggested reimbursement as a transfer. */
    fun recordSettlement(settlement: SettlementRow) {
        viewModelScope.launch {
            val id = Repository.newId()
            repository.saveExpense(
                ExpenseEntity(
                    id = id,
                    groupId = groupId,
                    title = "",
                    amountMinor = settlement.amount,
                    payerId = settlement.from.id,
                    date = Dates.todayUtcMillis(),
                    kind = ExpenseKind.TRANSFER,
                    createdAt = Repository.now(),
                ),
                listOf(ShareEntity(id, settlement.to.id, Weight.ONE)),
            )
        }
    }

    /** Writes the PDF report off the main thread; [onResult] gets its shareable URI, or null on failure. */
    fun exportPdf(context: Context, onResult: (Uri?) -> Unit) {
        val appContext = context.applicationContext
        val snapshot = state.value
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                runCatching { PdfReport.export(appContext, snapshot) }
                    .onFailure { Log.e(TAG, "PDF export failed", it) }
                    .getOrNull()
            }
            onResult(uri)
        }
    }

    fun deleteGroup() {
        viewModelScope.launch { repository.deleteGroup(groupId) }
    }
}
