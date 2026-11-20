package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalancesTest {

    private val members = listOf("alice", "bob", "carol")

    @Test
    fun weightedExpenseBalances() {
        // Alice (couple, 2) pays 120.00 shared with Bob (1) and Carol (1)
        val entries = listOf(Entry("alice", 12000, mapOf("alice" to 200, "bob" to 100, "carol" to 100)))
        val balances = Balances.compute(members, entries)
        assertEquals(mapOf("alice" to 6000L, "bob" to -3000L, "carol" to -3000L), balances)
        assertEquals(0L, balances.values.sum())
    }

    @Test
    fun transferCancelsDebt() {
        val entries = listOf(
            Entry("alice", 10000, mapOf("alice" to 100, "bob" to 100)),
            Entry("bob", 5000, mapOf("alice" to 100)),
        )
        assertEquals(0L, Balances.compute(listOf("alice", "bob"), entries).values.count { it != 0L }.toLong())
    }

    @Test
    fun summarySeparatesExpensesAndReimbursements() {
        val entries = listOf(
            // Alice (couple, 2) pays 90.00 shared with Bob (1)
            Entry("alice", 9000, mapOf("alice" to 200, "bob" to 100)),
            // Bob reimburses 20.00 of his 30.00
            Entry("bob", 2000, mapOf("alice" to 100), isTransfer = true),
        )
        val summaries = Balances.summarize(listOf("alice", "bob"), entries)
        assertEquals(MemberSummary(paid = 9000, share = 6000, transfersReceived = 2000), summaries["alice"])
        assertEquals(MemberSummary(share = 3000, transfersSent = 2000), summaries["bob"])
        assertEquals(1000L, summaries.getValue("alice").balance)
        assertEquals(-1000L, summaries.getValue("bob").balance)
    }

    @Test
    fun membersWithoutEntriesHaveZeroBalance() {
        assertEquals(mapOf("alice" to 0L, "bob" to 0L, "carol" to 0L), Balances.compute(members, emptyList()))
    }

    @Test
    fun settlementsClearAllBalances() {
        val balances = mapOf("alice" to 7000L, "bob" to -2500L, "carol" to -4500L, "dave" to 0L)
        val settlements = Balances.settle(balances)
        assertEquals(
            listOf(Settlement("carol", "alice", 4500), Settlement("bob", "alice", 2500)),
            settlements,
        )
        val after = balances.toMutableMap()
        settlements.forEach {
            after[it.fromId] = after.getValue(it.fromId) + it.amount
            after[it.toId] = after.getValue(it.toId) - it.amount
        }
        assertTrue(after.values.all { it == 0L })
    }

    @Test
    fun noSettlementWhenBalanced() {
        assertEquals(emptyList<Settlement>(), Balances.settle(mapOf("alice" to 0L, "bob" to 0L)))
    }
}
