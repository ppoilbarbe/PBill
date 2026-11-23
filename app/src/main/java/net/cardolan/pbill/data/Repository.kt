package net.cardolan.pbill.data

import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.flow.Flow
import net.cardolan.pbill.domain.MemberNames
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
     * The name is trimmed; returns false, saving nothing, when another member of the tab
     * already has it.
     */
    suspend fun saveMember(
        groupId: String,
        existing: MemberEntity?,
        name: String,
        weight: Int,
        startDate: Long,
        endDate: Long?,
    ): Boolean {
        val others = dao.getMembers(groupId).filter { it.id != existing?.id }.map { it.name }
        if (MemberNames.isTaken(name, others)) return false
        val trimmed = MemberNames.normalize(name)
        val member = existing?.copy(name = trimmed, weight = weight, startDate = startDate, endDate = endDate)
            ?: MemberEntity(newId(), groupId, trimmed, weight, now(), startDate, endDate)
        dao.upsertMember(member)
        return true
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

    /** Backup of every tab, as JSON; returns the text and the number of tabs. */
    suspend fun exportAll(): Pair<String, Int> {
        val tabs = dao.getAllTabs()
        return BackupJson.encode(tabs) to tabs.size
    }

    /**
     * Imports a backup: each tab replaces the one with the same id, others are added, and the
     * tabs absent from the backup are kept. Returns the number of tabs imported.
     * @throws BackupFormatException if the text is not a valid backup
     */
    suspend fun importAll(text: String): Int {
        val tabs = BackupJson.decode(text)
        try {
            dao.replaceTabs(tabs)
        } catch (e: SQLiteConstraintException) {
            // e.g. a member id already used by another tab of the device
            throw BackupFormatException("Conflicting backup: ${e.message}", e)
        }
        return tabs.size
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
        fun now(): Long = System.currentTimeMillis()
    }
}
