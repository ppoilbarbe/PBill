package net.cardolan.pbill.domain

/**
 * Category of an expense, only used to show an icon. Stored by name in the
 * database, so the names must never change; an unknown name reads as [NONE].
 */
enum class ExpenseCategory {
    NONE,
    GROCERIES,
    RESTAURANT,
    BAR,
    ACCOMMODATION,
    CAR,
    FUEL,
    TOLLS,
    TRAVEL,
    LEISURE,
    HOUSING,
    BILLS,
    HEALTH,
    GIFTS,
    ;

    companion object {
        fun fromKey(key: String?): ExpenseCategory = entries.firstOrNull { it.name == key } ?: NONE
    }
}
