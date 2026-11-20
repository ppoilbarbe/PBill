package net.cardolan.pbill.domain

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Member weights, stored as integers in hundredths to keep computations exact
 * (100 = weight 1, 250 = weight 2.5).
 */
object Weight {
    const val ONE = 100
    const val MAX = 100 * ONE

    /**
     * Parses a user-entered weight ("2", "2.5" or "2,5"), from 0 to 100; null when invalid.
     * A weight of 0 marks someone present who does not share the expenses.
     */
    fun parse(text: String): Int? {
        val value = Numbers.parseDecimal(text) ?: return null
        if (value.signum() < 0 || value.stripTrailingZeros().scale() > 2) return null
        val hundredths = runCatching { value.movePointRight(2).intValueExact() }.getOrNull() ?: return null
        return hundredths.takeIf { it <= MAX }
    }

    /** Formats a weight with the locale decimal separator, without useless zeros. */
    fun format(weight: Int, locale: Locale = Locale.getDefault()): String {
        val plain = BigDecimal.valueOf(weight.toLong(), 2).stripTrailingZeros().toPlainString()
        return plain.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }
}
