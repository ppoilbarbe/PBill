package net.cardolan.pbill.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Amounts are stored as Long values in the minor unit of the group currency (cents). */
object Money {
    fun fractionDigits(currencyCode: String): Int =
        runCatching { Currency.getInstance(currencyCode).defaultFractionDigits }
            .getOrDefault(2)
            .coerceAtLeast(0)

    /** Parses a user-entered amount ("12", "12.5", "12,50"); null when invalid. */
    fun parse(text: String, currencyCode: String): Long? {
        val value = Numbers.parseDecimal(text) ?: return null
        val digits = fractionDigits(currencyCode)
        if (value.stripTrailingZeros().scale() > digits) return null
        return runCatching { value.movePointRight(digits).longValueExact() }.getOrNull()
    }

    fun format(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currencyCode) }
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(BigDecimal.valueOf(minor, digits))
    }

    /**
     * Converts a stored amount when the group currency changes: the shown value is kept (no
     * exchange rate), only the number of minor units changes (EUR cents → JPY yen). Rounds
     * half up when the new currency has fewer decimals.
     */
    fun rescale(minor: Long, fromCode: String, toCode: String): Long {
        val from = fractionDigits(fromCode)
        val to = fractionDigits(toCode)
        if (from == to) return minor
        return BigDecimal.valueOf(minor, from).setScale(to, RoundingMode.HALF_UP).unscaledValue().longValueExact()
    }

    /** Plain editable representation of an amount ("12,50" in French). */
    fun toInput(minor: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        val plain = BigDecimal.valueOf(minor, fractionDigits(currencyCode)).toPlainString()
        return plain.replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    }
}

internal object Numbers {
    /** Accepts both '.' and ',' as decimal separator and ignores spaces. */
    fun parseDecimal(text: String): BigDecimal? {
        val cleaned = text.filterNot { it.isWhitespace() || it == ' ' || it == ' ' }
            .replace(',', '.')
        if (cleaned.isEmpty() || cleaned.count { it == '.' } > 1) return null
        if (!cleaned.all { it.isDigit() || it == '.' || it == '-' }) return null
        return cleaned.toBigDecimalOrNull()
    }
}
