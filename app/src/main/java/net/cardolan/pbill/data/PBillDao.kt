package net.cardolan.pbill.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PBillDao {

    // ── Groups ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM expense_groups ORDER BY createdAt DESC")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    fun observeGroup(id: String): Flow<GroupEntity?>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    suspend fun getGroup(id: String): GroupEntity?

    @Upsert
    suspend fun upsertGroup(group: GroupEntity)

    @Query("DELETE FROM expense_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    /**
     * Renames a group and changes its currency; [rescale] maps every stored amount from the old
     * currency minor unit to the new one.
     */
    @Transaction
    suspend fun updateGroup(id: String, name: String, currency: String, rescale: (Long, String, String) -> Long) {
        val group = getGroup(id) ?: return
        if (group.currency != currency) {
            val expenses = getExpenses(id)
            updateExpenses(expenses.map { it.copy(amountMinor = rescale(it.amountMinor, group.currency, currency)) })
        }
        upsertGroup(group.copy(name = name, currency = currency))
    }

    // ── Members ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY createdAt")
    fun observeMembers(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM members WHERE groupId = :groupId ORDER BY createdAt")
    suspend fun getMembers(groupId: String): List<MemberEntity>

    @Upsert
    suspend fun upsertMember(member: MemberEntity)

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun deleteMember(id: String)

    @Query(
        "SELECT (SELECT COUNT(*) FROM expenses WHERE payerId = :memberId) + " +
            "(SELECT COUNT(*) FROM expense_shares WHERE memberId = :memberId)",
    )
    suspend fun countMemberUsage(memberId: String): Int

    // ── Expenses ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM expenses WHERE groupId = :groupId ORDER BY date DESC, createdAt DESC")
    fun observeExpenses(groupId: String): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT s.* FROM expense_shares s INNER JOIN expenses e ON e.id = s.expenseId " +
            "WHERE e.groupId = :groupId",
    )
    fun observeShares(groupId: String): Flow<List<ShareEntity>>

    @Query("SELECT * FROM expenses WHERE groupId = :groupId")
    suspend fun getExpenses(groupId: String): List<ExpenseEntity>

    @Update
    suspend fun updateExpenses(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpense(id: String): ExpenseEntity?

    @Query("SELECT * FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun getShares(expenseId: String): List<ShareEntity>

    // Upsert (not REPLACE) so that updating an expense does not cascade-delete its shares
    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Insert
    suspend fun insertShares(shares: List<ShareEntity>)

    @Query("DELETE FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun deleteShares(expenseId: String)

    @Transaction
    suspend fun saveExpense(expense: ExpenseEntity, shares: List<ShareEntity>) {
        upsertExpense(expense)
        deleteShares(expense.id)
        insertShares(shares)
    }

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpense(id: String)
}
