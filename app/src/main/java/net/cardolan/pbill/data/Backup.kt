package net.cardolan.pbill.data

import net.cardolan.pbill.domain.ExpenseCategory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Currency

/** A tab with everything it contains, as saved in a backup file. */
data class TabBackup(
    val group: GroupEntity,
    val members: List<MemberEntity>,
    val expenses: List<ExpenseEntity>,
    val shares: List<ShareEntity>,
)

/** The file is not a PBill backup, or it is damaged or inconsistent. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * JSON backup of tabs. Values are the stored ones (amounts in the currency minor unit, weights
 * in hundredths), so that nothing is lost; days are written as ISO dates for readability.
 */
object BackupJson {
    private const val FORMAT = "pbill"
    const val VERSION = 1

    fun encode(tabs: List<TabBackup>): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("tabs", JSONArray(tabs.map(::encodeTab)))
        return root.toString(2)
    }

    private fun encodeTab(tab: TabBackup): JSONObject {
        val sharesByExpense = tab.shares.groupBy { it.expenseId }
        return JSONObject()
            .put("id", tab.group.id)
            .put("name", tab.group.name)
            .put("currency", tab.group.currency)
            .put("createdAt", tab.group.createdAt)
            .put("members", JSONArray(tab.members.map { member ->
                JSONObject()
                    .put("id", member.id)
                    .put("name", member.name)
                    .put("weight", member.weight)
                    .put("createdAt", member.createdAt)
                    .put("startDate", day(member.startDate))
                    .put("endDate", member.endDate?.let(::day) ?: JSONObject.NULL)
            }))
            .put("expenses", JSONArray(tab.expenses.map { expense ->
                JSONObject()
                    .put("id", expense.id)
                    .put("title", expense.title)
                    .put("amountMinor", expense.amountMinor)
                    .put("payerId", expense.payerId)
                    .put("date", day(expense.date))
                    .put("kind", expense.kind)
                    .put("category", expense.category)
                    .put("createdAt", expense.createdAt)
                    .put("shares", JSONArray(sharesByExpense[expense.id].orEmpty().map { share ->
                        JSONObject().put("memberId", share.memberId).put("weight", share.weight)
                    }))
            }))
    }

    /** Reads and checks a backup; throws [BackupFormatException] if it cannot be imported as is. */
    fun decode(text: String): List<TabBackup> {
        try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) throw BackupFormatException("Not a PBill backup")
            val version = root.getInt("version")
            if (version < 1 || version > VERSION) throw BackupFormatException("Unsupported version $version")
            val tabs = root.getJSONArray("tabs").objects().map(::decodeTab)
            check(tabs.map { it.group.id }, "tab")
            check(tabs.flatMap { it.members }.map { it.id }, "member")
            check(tabs.flatMap { it.expenses }.map { it.id }, "expense")
            return tabs
        } catch (e: JSONException) {
            throw BackupFormatException("Malformed backup: ${e.message}", e)
        } catch (e: DateTimeParseException) {
            throw BackupFormatException("Malformed date: ${e.message}", e)
        }
    }

    private fun decodeTab(json: JSONObject): TabBackup {
        val groupId = json.getString("id")
        val currency = json.getString("currency")
        require(runCatching { Currency.getInstance(currency) }.isSuccess, "unknown currency $currency")
        val group = GroupEntity(groupId, json.getString("name"), currency, json.getLong("createdAt"))
        val members = json.getJSONArray("members").objects().map { m ->
            MemberEntity(
                id = m.getString("id"),
                groupId = groupId,
                name = m.getString("name"),
                weight = m.getInt("weight").also { require(it >= 0, "negative weight") },
                createdAt = m.getLong("createdAt"),
                startDate = parseDay(m.getString("startDate")),
                endDate = if (m.isNull("endDate")) null else parseDay(m.getString("endDate")),
            )
        }
        val memberIds = members.map { it.id }.toSet()
        val shares = mutableListOf<ShareEntity>()
        val expenses = json.getJSONArray("expenses").objects().map { e ->
            val id = e.getString("id")
            val kind = e.getString("kind")
            require(kind == ExpenseKind.EXPENSE || kind == ExpenseKind.TRANSFER, "unknown kind $kind")
            val payerId = e.getString("payerId")
            require(payerId in memberIds, "payer $payerId is not a member of the tab")
            val expenseShares = e.getJSONArray("shares").objects().map { s ->
                ShareEntity(id, s.getString("memberId"), s.getInt("weight"))
            }
            expenseShares.forEach {
                require(it.memberId in memberIds, "participant ${it.memberId} is not a member of the tab")
                require(it.weight >= 0, "negative weight")
            }
            check(expenseShares.map { it.memberId }, "participant")
            shares += expenseShares
            ExpenseEntity(
                id = id,
                groupId = groupId,
                title = e.getString("title"),
                amountMinor = e.getLong("amountMinor").also { require(it >= 0, "negative amount") },
                payerId = payerId,
                date = parseDay(e.getString("date")),
                kind = kind,
                createdAt = e.getLong("createdAt"),
                category = ExpenseCategory.fromKey(e.optString("category")).name,
            )
        }
        return TabBackup(group, members, expenses, shares)
    }

    private fun require(condition: Boolean, message: String) {
        if (!condition) throw BackupFormatException(message)
    }

    private fun check(ids: List<String>, what: String) {
        val duplicate = ids.groupBy { it }.entries.firstOrNull { it.value.size > 1 }?.key
        if (duplicate != null) throw BackupFormatException("Duplicate $what $duplicate")
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun day(utcMillis: Long): String =
        LocalDate.ofEpochDay(Math.floorDiv(utcMillis, 86_400_000L)).toString()

    private fun parseDay(text: String): Long =
        LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
