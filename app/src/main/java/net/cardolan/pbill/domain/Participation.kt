package net.cardolan.pbill.domain

/**
 * Who shares an expense by default. Expense and participation dates are days
 * stored as UTC midnight; a member takes part from their start date to their
 * end date (if any), both included.
 */
object Participation {

    /**
     * Whether a member takes part by default in an expense dated [expenseDate]:
     * a positive weight, and the date within their participation period.
     */
    fun sharesByDefault(weight: Int, startDate: Long, endDate: Long?, expenseDate: Long): Boolean =
        weight > 0 && expenseDate >= startDate && (endDate == null || expenseDate <= endDate)

    /** A period is valid when it has no end date, or ends on or after its start. */
    fun isValidPeriod(startDate: Long, endDate: Long?): Boolean = endDate == null || endDate >= startDate
}
