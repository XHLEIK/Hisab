package com.example.hisab.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.hisab.data.backup.BackupPreferences
import com.example.hisab.data.db.entity.AccountEntity
import com.example.hisab.data.db.entity.CategoryEntity
import com.example.hisab.data.export.ExportFormat
import com.example.hisab.data.model.TransactionType
import com.example.hisab.data.repository.AccountRepository
import com.example.hisab.data.repository.BackupRepository
import com.example.hisab.data.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hisab_settings")

class SettingsViewModel(
    private val categoryRepository: CategoryRepository,
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository? = null
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = categoryRepository
        .getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val accounts: StateFlow<List<AccountEntity>> = (accountRepository?.getAllAccounts() ?: MutableStateFlow(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    fun addCategory(name: String, type: TransactionType, iconName: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryRepository.insertCategory(
                CategoryEntity(
                    name = name,
                    type = type,
                    iconName = iconName,
                    colorHex = colorHex,
                    isDefault = false
                )
            )
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryRepository.updateCategory(category)
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryRepository.deleteCategory(category)
        }
    }

    fun addAccount(name: String, type: String) {
        val colorHex = when (type.uppercase()) {
            "PRIMARY" -> "#10B981"
            "SECONDARY" -> "#3B82F6"
            "SAVINGS", "SAVING" -> "#F59E0B"
            "CASH", "CASH WALLET" -> "#8B5CF6"
            else -> "#14B8A6"
        }
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.insertAccount(
                AccountEntity(name = name, type = type, isPrimary = false, colorHex = colorHex)
            )
        }
    }

    fun updateAccount(oldName: String, account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.updateAccount(oldName, account)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository?.deleteAccount(account)
        }
    }

    fun exportReport(context: Context, uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            backupRepository.exportReport(context, uri, format).fold(
                onSuccess = { count ->
                    _exportResult.value = "Exported $count records as ${format.displayName} successfully"
                },
                onFailure = { error ->
                    _exportResult.value = "Export failed: ${error.message}"
                }
            )
        }
    }

    fun smartImportBackup(context: Context, onFallbackToFilePicker: () -> Unit) {
        viewModelScope.launch {
            val success = backupRepository.smartImport(context)
            if (success) {
                _importResult.value = "Successfully restored backup from Documents/Hisab!"
            } else {
                onFallbackToFilePicker()
            }
        }
    }

    fun importBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            backupRepository.importBackup(context, uri).fold(
                onSuccess = { count ->
                    _importResult.value = "Imported $count records successfully"
                },
                onFailure = { error ->
                    _importResult.value = "Import failed: ${error.message}"
                }
            )
        }
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            BackupPreferences(context).setAutoBackupEnabled(enabled)
        }
    }

    fun clearMessage() {
        _exportResult.value = null
        _importResult.value = null
    }

    class Factory(
        private val categoryRepository: CategoryRepository,
        private val backupRepository: BackupRepository,
        private val accountRepository: AccountRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(categoryRepository, backupRepository, accountRepository) as T
        }
    }
}
