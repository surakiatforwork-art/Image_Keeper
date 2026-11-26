package com.branchphotovault.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.branchphotovault.data.model.BranchListItem
import com.branchphotovault.data.model.BranchSortOption
import com.branchphotovault.data.repository.BranchRepository
import com.branchphotovault.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BranchListUiState(
    val searchText: String = "",
    val selectedAccount: String? = null,
    val sortOption: BranchSortOption = BranchSortOption.ACCOUNT,
    val onlyWithPhotos: Boolean = false,
    val branches: List<BranchListItem> = emptyList(),
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BranchListViewModel(
    private val branchRepository: BranchRepository,
    settingsRepository: SettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    context: Context
) : ViewModel() {

    private data class InputsState(
        val searchText: String,
        val selectedAccount: String?,
        val sortOption: BranchSortOption,
        val isSyncing: Boolean,
        val message: String?
    )

    private val browsePreferences = context.applicationContext.getSharedPreferences(
        "keeper_branch_browse_filters_v1", Context.MODE_PRIVATE
    )
    private val searchText = MutableStateFlow(
        savedStateHandle["search_text"] ?: browsePreferences.getString("search_text", "").orEmpty()
    )
    private val selectedAccount = MutableStateFlow<String?>(
        savedStateHandle["account_filter"] ?: browsePreferences.getString("account_filter", null)
    )
    private val sortOption = MutableStateFlow(
        BranchSortOption.entries.firstOrNull {
            it.name == (
                savedStateHandle.get<String>("branch_sort")
                    ?: browsePreferences.getString("branch_sort", BranchSortOption.ACCOUNT.name)
            )
        } ?: BranchSortOption.ACCOUNT
    )
    private val onlyWithPhotos = MutableStateFlow(
        savedStateHandle["with_photos"]
            ?: browsePreferences.getBoolean("with_photos", false)
    )
    private val isSyncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private data class BranchFilters(val search: String, val account: String?, val sort: BranchSortOption, val withPhotos: Boolean)

    private val branchesFlow = combine(searchText, selectedAccount, sortOption, onlyWithPhotos) { search, account, sort, withPhotos ->
        BranchFilters(search, account, sort, withPhotos)
    }.flatMapLatest { (search, account, sort, withPhotos) ->
        branchRepository.observeBranches(
            searchText = search,
            accountFilter = account,
            sortOption = sort,
            onlyWithPhotos = withPhotos
        )
    }

    private val inputsFlow = combine(
        searchText,
        selectedAccount,
        sortOption,
        isSyncing,
        message
    ) { search, account, sort, syncing, userMessage ->
        InputsState(
            searchText = search,
            selectedAccount = account,
            sortOption = sort,
            isSyncing = syncing,
            message = userMessage
        )
    }

    val uiState: StateFlow<BranchListUiState> = combine(
        inputsFlow,
        branchesFlow,
        settingsRepository.lastSyncAtFlow,
        onlyWithPhotos
    ) { inputs, branches, lastSyncAt, withPhotos ->
        BranchListUiState(
            searchText = inputs.searchText,
            selectedAccount = inputs.selectedAccount,
            sortOption = inputs.sortOption,
            onlyWithPhotos = withPhotos,
            branches = branches,
            lastSyncAt = lastSyncAt,
            isSyncing = inputs.isSyncing,
            message = inputs.message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BranchListUiState()
    )

    fun updateSearch(value: String) {
        searchText.value = value
        savedStateHandle["search_text"] = value
        browsePreferences.edit().putString("search_text", value).apply()
    }

    fun updateAccountFilter(value: String?) {
        selectedAccount.value = value
        savedStateHandle["account_filter"] = value
        browsePreferences.edit().putString("account_filter", value).apply()
    }

    fun updateSortOption(value: BranchSortOption) {
        sortOption.value = value
        savedStateHandle["branch_sort"] = value.name
        browsePreferences.edit().putString("branch_sort", value.name).apply()
    }

    fun updateOnlyWithPhotos(value: Boolean) {
        onlyWithPhotos.value = value
        savedStateHandle["with_photos"] = value
        browsePreferences.edit().putBoolean("with_photos", value).apply()
    }

    fun syncBranches() {
        if (isSyncing.value) return

        viewModelScope.launch {
            isSyncing.value = true
            try {
                val count = branchRepository.syncBranches()
                message.value = "Synced $count branch(es)."
            } catch (error: Exception) {
                message.value = error.message ?: "Sync failed."
            } finally {
                isSyncing.value = false
            }
        }
    }

    fun clearMessage() {
        message.update { null }
    }

    companion object {
        fun provideFactory(
            branchRepository: BranchRepository,
            settingsRepository: SettingsRepository,
            context: Context
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: androidx.lifecycle.viewmodel.CreationExtras
                ): T {
                    return BranchListViewModel(
                        branchRepository, settingsRepository,
                        extras.createSavedStateHandle(),
                        context
                    ) as T
                }
            }
        }
    }
}
