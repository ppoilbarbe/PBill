package net.cardolan.pbill.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class ParsingTest {

    @Test
    fun weightParsing() {
        assertEquals(100, Weight.parse("1"))
        assertEquals(250, Weight.parse("2,5"))
        assertEquals(250, Weight.parse("2.50"))
        assertEquals(133, Weight.parse(" 1.33 "))
        assertEquals(0, Weight.parse("0"))
        assertEquals(10000, Weight.parse("100"))
        assertNull(Weight.parse("100.01"))
        assertNull(Weight.parse("-1"))
        assertNull(Weight.parse("1.234"))
        assertNull(Weight.parse("abc"))
        assertNull(Weight.parse(""))
    }

    @Test
    fun weightFormatting() {
        assertEquals("1", Weight.format(100, Locale.US))
        assertEquals("2.5", Weight.format(250, Locale.US))
        assertEquals("2,5", Weight.format(250, Locale.FRANCE))
        assertEquals("10", Weight.format(1000, Locale.US))
    }

    @Test
    fun moneyParsing() {
        assertEquals(1250L, Money.parse("12,50", "EUR"))
        assertEquals(1250L, Money.parse("12.5", "EUR"))
        assertEquals(1200L, Money.parse("12", "EUR"))
        assertEquals(1500L, Money.parse("1 500", "JPY"))
        assertNull(Money.parse("12.345", "EUR"))
        assertNull(Money.parse("1.5", "JPY"))
        assertNull(Money.parse("1.2.3", "EUR"))
    }

    @Test
    fun moneyInput() {
        assertEquals("12,50", Money.toInput(1250, "EUR", Locale.FRANCE))
        assertEquals("1500", Money.toInput(1500, "JPY", Locale.US))
    }

    @Test
    fun moneyRescaling() {
        // Same number of decimals: unchanged
        assertEquals(1234L, Money.rescale(1234, "EUR", "USD"))
        // Fewer decimals: rounded half up (12.34 → 12, 12.50 → 13)
        assertEquals(12L, Money.rescale(1234, "EUR", "JPY"))
        assertEquals(13L, Money.rescale(1250, "EUR", "JPY"))
        // More decimals: exact
        assertEquals(1200L, Money.rescale(12, "JPY", "EUR"))
        assertEquals(12340L, Money.rescale(1234, "EUR", "KWD"))
    }
}
