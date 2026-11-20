package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrenciesTest {

    @Test
    fun localeCurrencyComesFirstThenCommonOnes() {
        val codes = Currencies.list(Locale.FRANCE).map { it.currencyCode }
        assertEquals(listOf("EUR", "USD", "GBP", "CHF", "CAD", "JPY"), codes.take(6))
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun nonCurrencyCodesAreExcluded() {
        val codes = Currencies.list(Locale.US).map { it.currencyCode }
        assertFalse("XAU" in codes)
        assertFalse("XXX" in codes)
        assertTrue("XOF" in codes)
    }

    @Test
    fun filterMatchesCodeOrName() {
        val all = Currencies.list(Locale.FRANCE)
        assertEquals(listOf("CHF"), Currencies.filter(all, "chf", Locale.FRANCE).map { it.currencyCode })
        assertTrue(Currencies.filter(all, "franc suisse", Locale.FRANCE).any { it.currencyCode == "CHF" })
        assertEquals(all, Currencies.filter(all, " ", Locale.FRANCE))
    }
}
