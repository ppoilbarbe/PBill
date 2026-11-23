package net.cardolan.pbill.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonTest {

    private val day = 86_400_000L

    private val trip = TabBackup(
        group = GroupEntity("g1", "Trip", "EUR", 1_000L),
        members = listOf(
            MemberEntity("m1", "g1", "Alice", 100, 1_001L, startDate = 20_000 * day),
            MemberEntity("m2", "g1", "Bob & Carol", 250, 1_002L, startDate = 20_000 * day, endDate = 20_010 * day),
        ),
        expenses = listOf(
            ExpenseEntity("e1", "g1", "Fuel \"diesel\"", 6_543L, "m1", 20_005 * day, ExpenseKind.EXPENSE, 1_003L, "FUEL"),
            ExpenseEntity("e2", "g1", "Refund", 1_000L, "m2", 20_006 * day, ExpenseKind.TRANSFER, 1_004L),
        ),
        shares = listOf(
            ShareEntity("e1", "m1", 100),
            ShareEntity("e1", "m2", 250),
            ShareEntity("e2", "m1", 100),
        ),
    )

    private val flat = TabBackup(GroupEntity("g2", "Flat", "JPY", 2_000L), emptyList(), emptyList(), emptyList())

    @Test
    fun roundTripKeepsEverything() {
        val decoded = BackupJson.decode(BackupJson.encode(listOf(trip, flat)))
        assertEquals(listOf(trip, flat), decoded)
    }

    @Test
    fun daysAreWrittenAsIsoDates() {
        val json = JSONObject(BackupJson.encode(listOf(trip)))
        val member = json.getJSONArray("tabs").getJSONObject(0).getJSONArray("members").getJSONObject(1)
        assertEquals("2024-10-04", member.getString("startDate"))
        assertEquals("2024-10-14", member.getString("endDate"))
    }

    @Test
    fun unknownCategoryReadsAsNone() {
        val text = BackupJson.encode(listOf(trip)).replace("\"FUEL\"", "\"SPACESHIP\"")
        assertEquals("NONE", BackupJson.decode(text)[0].expenses[0].category)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = JSONObject(BackupJson.encode(listOf(trip)))
        json.put("exportedBy", "a future version")
        val tab = json.getJSONArray("tabs").getJSONObject(0)
        tab.put("color", "blue")
        tab.getJSONArray("members").getJSONObject(0).put("email", "alice@example.org")
        val expense = tab.getJSONArray("expenses").getJSONObject(0)
        expense.put("receipt", JSONObject().put("file", "receipt.jpg"))
        expense.getJSONArray("shares").getJSONObject(0).put("note", "half")
        assertEquals(listOf(trip), BackupJson.decode(json.toString()))
    }

    @Test
    fun missingRequiredFieldRejectsTheWholeFile() {
        val required = mapOf(
            "tab" to listOf("id", "name", "currency", "createdAt", "members", "expenses"),
            "member" to listOf("id", "name", "weight", "createdAt", "startDate"),
            "expense" to listOf("id", "title", "amountMinor", "payerId", "date", "kind", "createdAt", "shares"),
            "share" to listOf("memberId", "weight"),
        )
        required.forEach { (what, fields) ->
            fields.forEach { field ->
                val json = JSONObject(BackupJson.encode(listOf(trip, flat)))
                val tab = json.getJSONArray("tabs").getJSONObject(0)
                val expense = tab.getJSONArray("expenses").getJSONObject(0)
                val target = when (what) {
                    "tab" -> tab
                    "member" -> tab.getJSONArray("members").getJSONObject(0)
                    "expense" -> expense
                    else -> expense.getJSONArray("shares").getJSONObject(0)
                }
                target.remove(field)
                assertThrows("$what.$field", BackupFormatException::class.java) { BackupJson.decode(json.toString()) }
            }
        }
        listOf("format", "version", "tabs").forEach { field ->
            val json = JSONObject(BackupJson.encode(listOf(trip)))
            json.remove(field)
            assertThrows(field, BackupFormatException::class.java) { BackupJson.decode(json.toString()) }
        }
    }

    @Test
    fun optionalFieldsMayBeMissing() {
        val json = JSONObject(BackupJson.encode(listOf(trip)))
        val tab = json.getJSONArray("tabs").getJSONObject(0)
        tab.getJSONArray("members").getJSONObject(1).remove("endDate")
        tab.getJSONArray("expenses").getJSONObject(0).remove("category")
        val decoded = BackupJson.decode(json.toString()).single()
        assertEquals(null, decoded.members[1].endDate)
        assertEquals("NONE", decoded.expenses[0].category)
    }

    @Test
    fun rejectsInvalidFiles() {
        val valid = BackupJson.encode(listOf(trip))
        val invalid = listOf(
            "not json at all",
            "[]",
            valid.replace("\"pbill\"", "\"other\""),
            valid.replace("\"version\": 1", "\"version\": ${BackupJson.VERSION + 1}"),
            valid.replace("\"payerId\": \"m2\"", "\"payerId\": \"nobody\""),
            valid.replace("\"EUR\"", "\"XYZW\""),
            valid.replace("\"2024-10-14\"", "\"14/10/2024\""),
            valid.replace("\"kind\": \"TRANSFER\"", "\"kind\": \"GIFT\""),
            BackupJson.encode(listOf(trip, trip)),
        )
        invalid.forEach { text ->
            if (text != valid) {
                assertThrows(text, BackupFormatException::class.java) { BackupJson.decode(text) }
            }
        }
        // Each replacement must actually have changed the file
        assertEquals(invalid.size, invalid.count { it != valid })
    }
}
