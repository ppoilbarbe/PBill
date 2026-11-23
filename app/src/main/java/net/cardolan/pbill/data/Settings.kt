package net.cardolan.pbill.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.cardolan.pbill.domain.Currencies
import java.util.Currency

/** User preferences, stored on the device. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _defaultCurrency = MutableStateFlow(readDefaultCurrency())
    private val _reopenLastTab = MutableStateFlow(prefs.getBoolean(KEY_REOPEN_LAST_TAB, false))

    /** Currency suggested for a new tab: the chosen one, else the currency of the device region. */
    val defaultCurrency: StateFlow<String> = _defaultCurrency.asStateFlow()

    fun setDefaultCurrency(code: String) {
        prefs.edit { putString(KEY_DEFAULT_CURRENCY, code) }
        _defaultCurrency.value = code
    }

    /** Whether the app starts on the last tab opened, if it still exists. */
    val reopenLastTab: StateFlow<Boolean> = _reopenLastTab.asStateFlow()

    fun setReopenLastTab(value: Boolean) {
        prefs.edit { putBoolean(KEY_REOPEN_LAST_TAB, value) }
        _reopenLastTab.value = value
    }

    /**
     * Last tab opened (null: none yet). Read when the screen starts, not when the process does:
     * the process may outlive the screen once the app was left with the back button.
     */
    fun lastTab(): String? = prefs.getString(KEY_LAST_TAB, null)

    /** Records the tab being shown; going back to the list of tabs does not clear it. */
    fun setLastTab(groupId: String) = prefs.edit { putString(KEY_LAST_TAB, groupId) }

    private fun readDefaultCurrency(): String =
        prefs.getString(KEY_DEFAULT_CURRENCY, null)
            ?.takeIf { runCatching { Currency.getInstance(it) }.isSuccess }
            ?: Currencies.list().first().currencyCode

    private companion object {
        const val KEY_DEFAULT_CURRENCY = "default_currency"
        const val KEY_REOPEN_LAST_TAB = "reopen_last_tab"
        const val KEY_LAST_TAB = "last_tab"
    }
}
