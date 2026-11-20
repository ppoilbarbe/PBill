package net.cardolan.pbill.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Who shares an expense by default. Expense dates are days stored as UTC
 * midnight; a member counts from the day (in the device time zone) they were
 * added to the tab, that day included.
 */
object Participation {

    /** Day a member was added, in the expense date convention (UTC midnight epoch milliseconds). */
    fun joinDay(createdAt: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Whether a member takes part by default in an expense dated [expenseDate]:
     * a positive weight, and added to the tab on or before that day.
     */
    fun sharesByDefault(weight: Int, createdAt: Long, expenseDate: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        weight > 0 && expenseDate >= joinDay(createdAt, zone)
}
