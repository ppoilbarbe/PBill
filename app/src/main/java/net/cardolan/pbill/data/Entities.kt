package net.cardolan.pbill.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Values of [ExpenseEntity.kind]. */
object ExpenseKind {
    /** Money spent for the group, shared between participants. */
    const val EXPENSE = "EXPENSE"

    /** Money given by one member to another (reimbursement). */
    const val TRANSFER = "TRANSFER"
}

// Ids are random UUIDs so that records can later be synchronised between devices.

@Entity(tableName = "expense_groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** ISO 4217 code; all amounts of the group are in its minor unit. */
    val currency: String,
    val createdAt: Long,
)

@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class MemberEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    /** A person or a household represented by a single payer. */
    val name: String,
    /** Default share weight in hundredths (100 = 1, 250 = 2.5). */
    val weight: Int,
    val createdAt: Long,
    /**
     * Participation period, as days in the expense date convention (UTC midnight epoch
     * milliseconds), both included; no end date means still taking part. Only the default
     * participants of new expenses depend on it: recorded expenses keep their shares.
     */
    val startDate: Long,
    val endDate: Long? = null,
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["payerId"]),
    ],
    indices = [Index("groupId"), Index("payerId")],
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val amountMinor: Long,
    val payerId: String,
    /** Day of the expense, as UTC midnight epoch milliseconds. */
    val date: Long,
    val kind: String,
    val createdAt: Long,
    /** [net.cardolan.pbill.domain.ExpenseCategory] name; always NONE for transfers. */
    val category: String = "NONE",
)

/**
 * Participant of an expense. The weight is copied from the member when the
 * expense is created, so changing a household's weight later (a child is born)
 * does not rewrite past expenses.
 */
@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "memberId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = MemberEntity::class, parentColumns = ["id"], childColumns = ["memberId"]),
    ],
    indices = [Index("memberId")],
)
data class ShareEntity(
    val expenseId: String,
    val memberId: String,
    val weight: Int,
)
