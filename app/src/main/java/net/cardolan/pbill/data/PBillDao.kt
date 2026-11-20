package net.cardolan.pbill.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT id FROM expenses WHERE groupId = :groupId AND kind = :kind AND date >= :fromDate")
    suspend fun getExpenseIdsFrom(groupId: String, kind: String, fromDate: Long): List<String>

    /** Adds a new member, sharing the expenses dated from [fromDate] (already recorded, e.g. planned ones). */
    @Transaction
    suspend fun insertMember(member: MemberEntity, fromDate: Long) {
        upsertMember(member)
        if (member.weight > 0) {
            val expenseIds = getExpenseIdsFrom(member.groupId, ExpenseKind.EXPENSE, fromDate)
            insertShares(expenseIds.map { ShareEntity(it, member.id, member.weight) })
        }
    }
}
