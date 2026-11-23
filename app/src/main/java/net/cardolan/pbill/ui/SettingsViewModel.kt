package net.cardolan.pbill.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.cardolan.pbill.R
import net.cardolan.pbill.data.BackupFormatException
import net.cardolan.pbill.data.Repository
import java.io.IOException

/** Outcome of an export or an import, shown once in a snackbar. */
sealed interface BackupResult {
    fun message(context: Context): String

    data class Exported(val count: Int) : BackupResult {
        override fun message(context: Context) =
            context.resources.getQuantityString(R.plurals.export_done, count, count)
    }

    data class Imported(val count: Int) : BackupResult {
        override fun message(context: Context) =
            context.resources.getQuantityString(R.plurals.import_done, count, count)
    }

    data object Invalid : BackupResult {
        override fun message(context: Context) = context.getString(R.string.import_invalid)
    }

    data object IoError : BackupResult {
        override fun message(context: Context) = context.getString(R.string.backup_io_error)
    }
}

class SettingsViewModel(private val repository: Repository) : ViewModel() {

    private val _result = MutableStateFlow<BackupResult?>(null)
    val result: StateFlow<BackupResult?> = _result.asStateFlow()

    fun clearResult() {
        _result.value = null
    }

    fun export(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _result.value = try {
            val (text, count) = repository.exportAll()
            withContext(Dispatchers.IO) {
                val stream = resolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
                stream.bufferedWriter().use { it.write(text) }
            }
            BackupResult.Exported(count)
        } catch (e: IOException) {
            Log.w(TAG, "Export failed", e)
            BackupResult.IoError
        }
    }

    fun import(resolver: ContentResolver, uri: Uri) = viewModelScope.launch {
        _result.value = try {
            val text = withContext(Dispatchers.IO) {
                val stream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
                stream.bufferedReader().use { it.readText() }
            }
            BackupResult.Imported(repository.importAll(text))
        } catch (e: BackupFormatException) {
            Log.w(TAG, "Invalid backup", e)
            BackupResult.Invalid
        } catch (e: IOException) {
            Log.w(TAG, "Import failed", e)
            BackupResult.IoError
        }
    }

    private companion object {
        const val TAG = "PBillBackup"
    }
}
