package net.cardolan.pbill.domain

/** One expense or transfer: [payerId] paid [amount], owed by [shares] (member id → weight). */
data class Entry(
    val payerId: String,
    val amount: Long,
    val shares: Map<String, Int>,
    val isTransfer: Boolean = false,
)

/** What a member paid, owes, and reimbursed or received so far. */
data class MemberSummary(
    /** Expenses paid for the group. */
    val paid: Long = 0,
    /** Part of the expenses owed according to the weights. */
    val share: Long = 0,
    /** Reimbursements given to other members. */
    val transfersSent: Long = 0,
    /** Reimbursements received from other members. */
    val transfersReceived: Long = 0,
) {
    /** Positive: the group owes the member money. */
    val balance: Long get() = paid - share + transfersSent - transfersReceived
}

/** Suggested payment: [fromId] gives [amount] to [toId]. */
data class Settlement(val fromId: String, val toId: String, val amount: Long)

object Balances {
    /** Per-member totals, split between expenses and reimbursements. */
    fun summarize(memberIds: Collection<String>, entries: List<Entry>): Map<String, MemberSummary> {
        val summaries = LinkedHashMap<String, MemberSummary>()
        memberIds.forEach { summaries[it] = MemberSummary() }
        fun update(id: String, change: (MemberSummary) -> MemberSummary) {
            summaries[id] = change(summaries[id] ?: MemberSummary())
        }
        for (entry in entries) {
            if (entry.shares.isEmpty() || entry.shares.values.sum() <= 0) continue
            val parts = Split.byWeights(entry.amount, entry.shares)
            if (entry.isTransfer) {
                update(entry.payerId) { it.copy(transfersSent = it.transfersSent + entry.amount) }
                parts.forEach { (id, part) -> update(id) { it.copy(transfersReceived = it.transfersReceived + part) } }
            } else {
                update(entry.payerId) { it.copy(paid = it.paid + entry.amount) }
                parts.forEach { (id, part) -> update(id) { it.copy(share = it.share + part) } }
            }
        }
        return summaries
    }

    /**
     * Net balance of each member: what they paid minus their share of what was
     * spent. Positive means the group owes them money. Balances always sum to 0.
     */
    fun compute(memberIds: Collection<String>, entries: List<Entry>): Map<String, Long> =
        summarize(memberIds, entries).mapValues { it.value.balance }

    /**
     * Greedy settle-up: repeatedly matches the largest debtor with the largest
     * creditor. Produces at most n - 1 payments.
     */
    fun settle(balances: Map<String, Long>): List<Settlement> {
        class Position(val id: String, var amount: Long)

        val order = compareByDescending<Position> { it.amount }.thenBy { it.id }
        val creditors = balances.filter { it.value > 0 }.map { Position(it.key, it.value) }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { Position(it.key, -it.value) }.toMutableList()
        val result = mutableListOf<Settlement>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            creditors.sortWith(order)
            debtors.sortWith(order)
            val creditor = creditors.first()
            val debtor = debtors.first()
            val amount = minOf(creditor.amount, debtor.amount)
            result += Settlement(debtor.id, creditor.id, amount)
            creditor.amount -= amount
            debtor.amount -= amount
            if (creditor.amount == 0L) creditors.removeAt(0)
            if (debtor.amount == 0L) debtors.removeAt(0)
        }
        return result
    }
}
