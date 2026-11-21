package net.cardolan.pbill.data

import kotlinx.coroutines.flow.Flow
import net.cardolan.pbill.domain.Money
import java.util.UUID

class Repository(private val dao: PBillDao) {

    fun groups(): Flow<List<GroupEntity>> = dao.observeGroups()
    fun group(id: String): Flow<GroupEntity?> = dao.observeGroup(id)
    fun members(groupId: String): Flow<List<MemberEntity>> = dao.observeMembers(groupId)
    fun expenses(groupId: String): Flow<List<ExpenseEntity>> = dao.observeExpenses(groupId)
    fun shares(groupId: String): Flow<List<ShareEntity>> = dao.observeShares(groupId)

    suspend fun getGroup(id: String): GroupEntity? = dao.getGroup(id)
    suspend fun getMembers(groupId: String): List<MemberEntity> = dao.getMembers(groupId)

    suspend fun getExpense(id: String): Pair<ExpenseEntity, List<ShareEntity>>? {
        val expense = dao.getExpense(id) ?: return null
        return expense to dao.getShares(id)
    }

    suspend fun createGroup(name: String, currency: String): String {
        val id = newId()
        dao.upsertGroup(GroupEntity(id, name, currency, now()))
        return id
    }

    /** Renames a group and changes its currency; amounts keep their value, rescaled to the new minor unit. */
    suspend fun updateGroup(id: String, name: String, currency: String) =
        dao.updateGroup(id, name, currency, Money::rescale)

    suspend fun deleteGroup(id: String) = dao.deleteGroup(id)

    /**
     * Saves a member. The participation period only sets the default participants of the
     * expenses recorded afterwards: the expenses already recorded are left unchanged.
     */
    suspend fun saveMember(
        groupId: String,
        existing: MemberEntity?,
        name: String,
        weight: Int,
        startDate: Long,
        endDate: Long?,
    ) {
        val member = existing?.copy(name = name, weight = weight, startDate = startDate, endDate = endDate)
            ?: MemberEntity(newId(), groupId, name, weight, now(), startDate, endDate)
        dao.upsertMember(member)
    }

    /** Deletes a member unless expenses reference them; returns whether it was deleted. */
    suspend fun deleteMember(id: String): Boolean {
        if (dao.countMemberUsage(id) > 0) return false
        dao.deleteMember(id)
        return true
    }

    suspend fun saveExpense(expense: ExpenseEntity, shares: List<ShareEntity>) =
        dao.saveExpense(expense, shares)

    suspend fun deleteExpense(id: String) = dao.deleteExpense(id)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun now(): Long = System.currentTimeMillis()
    }
}
