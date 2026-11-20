package net.cardolan.pbill.ui

import java.text.DateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

/** Expense dates are days, stored as UTC midnight (the convention of the Material date picker). */
object Dates {
    fun todayUtcMillis(): Long =
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun format(utcMillis: Long): String {
        val format = DateFormat.getDateInstance(DateFormat.MEDIUM)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(utcMillis))
    }
}
