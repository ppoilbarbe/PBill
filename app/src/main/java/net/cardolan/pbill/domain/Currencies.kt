package net.cardolan.pbill.domain

import java.util.Currency
import java.util.Locale

object Currencies {
    /** Shown first, after the locale currency. */
    private val COMMON = listOf("EUR", "USD", "GBP", "CHF", "CAD", "JPY")

    /** "X" codes that are real currencies (the others are metals, funds or test codes). */
    private val REAL_X_CODES = setOf("XAF", "XCD", "XOF", "XPF")

    /** Selectable currencies: locale currency, common ones, then the rest by code. */
    fun list(locale: Locale = Locale.getDefault()): List<Currency> {
        val all = Currency.getAvailableCurrencies()
            .filter { !it.currencyCode.startsWith("X") || it.currencyCode in REAL_X_CODES }
            .sortedBy { it.currencyCode }
        val localCode = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull()
        val first = (listOfNotNull(localCode) + COMMON).distinct()
        val byCode = all.associateBy { it.currencyCode }
        val head = first.mapNotNull { byCode[it] }
        return head + all.filter { it !in head }
    }

    /** Case-insensitive match on the code or the localized name. */
    fun filter(currencies: List<Currency>, query: String, locale: Locale = Locale.getDefault()): List<Currency> {
        val q = query.trim()
        if (q.isEmpty()) return currencies
        return currencies.filter {
            it.currencyCode.contains(q, ignoreCase = true) || it.getDisplayName(locale).contains(q, ignoreCase = true)
        }
    }

    fun label(currency: Currency, locale: Locale = Locale.getDefault()): String =
        "${currency.currencyCode} · ${currency.getDisplayName(locale)}"
}
