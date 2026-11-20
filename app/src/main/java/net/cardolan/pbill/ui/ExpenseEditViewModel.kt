package net.cardolan.pbill.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.cardolan.pbill.R
import net.cardolan.pbill.data.ExpenseEntity
import net.cardolan.pbill.data.ExpenseKind
import net.cardolan.pbill.data.MemberEntity
import net.cardolan.pbill.data.Repository
import net.cardolan.pbill.data.ShareEntity
import net.cardolan.pbill.domain.Money
import net.cardolan.pbill.domain.Participation
import net.cardolan.pbill.domain.Split
import net.cardolan.pbill.domain.Weight

/** [touched]: changed by the user, so no longer following the default for the expense date. */
data class ParticipantInput(
    val member: MemberEntity,
    val included: Boolean,
    val weightText: String,
    val touched: Boolean = false,
) {
    /** Weight of the share: 0 when unchecked, null when the entered weight is invalid. */
    val weight: Int? get() = if (included) Weight.parse(weightText) else 0
}

data class ExpenseEditState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val kind: String = ExpenseKind.EXPENSE,
    val currency: String = "EUR",
    val members: List<MemberEntity> = emptyList(),
    val title: String = "",
    val amountText: String = "",
    val payerId: String? = null,
    val recipientId: String? = null,
    val date: Long = Dates.todayUtcMillis(),
    val participants: List<ParticipantInput> = emptyList(),
    @StringRes val error: Int? = null,
    val done: Boolean = false,
) {
    /** Live preview of each participant's share, empty while the input is incomplete. */
    val sharePreview: Map<String, Long>
        get() {
            val amount = Money.parse(amountText, currency)?.takeIf { it > 0 } ?: return emptyMap()
            val weights = participants.associate { it.member.id to (it.weight ?: return emptyMap()) }
                .filterValues { it > 0 }
            return if (weights.isEmpty()) emptyMap() else Split.byWeights(amount, weights)
        }
}

class ExpenseEditViewModel(
    private val repository: Repository,
    private val groupId: String,
    private val expenseId: String?,
    kind: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseEditState(kind = kind))
    val state: StateFlow<ExpenseEditState> = _state.asStateFlow()

    private var createdAt: Long? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val group = repository.getGroup(groupId) ?: return _state.update { it.copy(done = true) }
        val members = repository.getMembers(groupId)
        val existing = expenseId?.let { repository.getExpense(it) }
        if (existing == null) {
            _state.update {
                it.copy(
                    loading = false,
                    currency = group.currency,
                    members = members,
                    payerId = members.firstOrNull()?.id,
                    recipientId = members.getOrNull(1)?.id,
                    participants = members.map { m -> ParticipantInput(m, sharesByDefault(m, it.date), Weight.format(m.weight)) },
                )
            }
            return
        }
        val (expense, shares) = existing
        createdAt = expense.createdAt
        val shareByMember = shares.associateBy { it.memberId }
        _state.update {
            it.copy(
                loading = false,
                isNew = false,
                kind = expense.kind,
                currency = group.currency,
                members = members,
                title = expense.title,
                amountText = Money.toInput(expense.amountMinor, group.currency),
                payerId = expense.payerId,
                recipientId = shares.firstOrNull()?.memberId,
                date = expense.date,
                participants = members.map { m ->
                    val share = shareByMember[m.id]
                    ParticipantInput(m, share != null, Weight.format(share?.weight ?: m.weight))
                },
            )
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, error = null) }
    fun setAmount(value: String) = _state.update { it.copy(amountText = value, error = null) }
    fun setPayer(id: String) = _state.update { it.copy(payerId = id, error = null) }
    fun setRecipient(id: String) = _state.update { it.copy(recipientId = id, error = null) }
    /** On a new expense, members the user has not changed follow the default for the new date. */
    fun setDate(value: Long) = _state.update { s ->
        val participants = if (!s.isNew) s.participants else s.participants.map {
            if (it.touched) it else it.copy(included = sharesByDefault(it.member, value))
        }
        s.copy(date = value, participants = participants)
    }

    private fun sharesByDefault(member: MemberEntity, date: Long) =
        Participation.sharesByDefault(member.weight, member.createdAt, date)

    /** Checking a member whose weight is 0 (or invalid) gives them a weight of 1. */
    fun setIncluded(memberId: String, included: Boolean) = updateParticipant(memberId) {
        val weightText = if (included && (Weight.parse(it.weightText) ?: 0) == 0) Weight.format(Weight.ONE) else it.weightText
        it.copy(included = included, weightText = weightText, touched = true)
    }
    fun setWeight(memberId: String, text: String) = updateParticipant(memberId) { it.copy(weightText = text, touched = true) }

    private fun updateParticipant(memberId: String, change: (ParticipantInput) -> ParticipantInput) =
        _state.update { s ->
            s.copy(
                participants = s.participants.map { if (it.member.id == memberId) change(it) else it },
                error = null,
            )
        }

    fun save() {
        val s = _state.value
        val isTransfer = s.kind == ExpenseKind.TRANSFER
        val error = when {
            !isTransfer && s.title.isBlank() -> R.string.error_title
            (Money.parse(s.amountText, s.currency) ?: 0L) <= 0L -> R.string.error_amount
            s.payerId == null -> R.string.error_payer
            isTransfer && (s.recipientId == null || s.recipientId == s.payerId) -> R.string.error_recipient
            !isTransfer && s.participants.any { it.weight == null } -> R.string.error_weight
            !isTransfer && s.participants.none { (it.weight ?: 0) > 0 } -> R.string.error_participants
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }
        val id = expenseId ?: Repository.newId()
        val shares = if (isTransfer) {
            listOf(ShareEntity(id, s.recipientId!!, Weight.ONE))
        } else {
            // a checked member with a weight of 0 does not take part, like an unchecked one
            s.participants.filter { it.weight!! > 0 }.map { ShareEntity(id, it.member.id, it.weight!!) }
        }
        val expense = ExpenseEntity(
            id = id,
            groupId = groupId,
            title = if (isTransfer) "" else s.title.trim(),
            amountMinor = Money.parse(s.amountText, s.currency)!!,
            payerId = s.payerId!!,
            date = s.date,
            kind = s.kind,
            createdAt = createdAt ?: Repository.now(),
        )
        viewModelScope.launch {
            repository.saveExpense(expense, shares)
            _state.update { it.copy(done = true) }
        }
    }

    fun delete() {
        val id = expenseId ?: return
        viewModelScope.launch {
            repository.deleteExpense(id)
            _state.update { it.copy(done = true) }
        }
    }
}
