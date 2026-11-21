package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpenseCategoryTest {

    @Test
    fun categoriesAreReadBackFromTheirStoredName() {
        ExpenseCategory.entries.forEach { assertEquals(it, ExpenseCategory.fromKey(it.name)) }
    }

    @Test
    fun unknownOrMissingCategoryReadsAsNone() {
        assertEquals(ExpenseCategory.NONE, ExpenseCategory.fromKey(null))
        assertEquals(ExpenseCategory.NONE, ExpenseCategory.fromKey("SPACESHIP"))
    }
}
