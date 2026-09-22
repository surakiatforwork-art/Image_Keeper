package com.branchphotovault.ui.viewmodel

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
    val sortOption: BranchSortOption = BranchSortOption.ROUTE,
    val branches: List<BranchListItem> = emptyList(),
    val lastSyncAt: Long? = null,
    val isSyncing: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BranchListViewModel(
    private val branchRepository: BranchRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private data class InputsState(
        val searchText: String,
        val selectedAccount: String?,
        val sortOption: BranchSortOption,
        val isSyncing: Boolean,
        val message: String?
    )

    private val searchText = MutableStateFlow("")
    private val selectedAccount = MutableStateFlow<String?>(null)
    private val sortOption = MutableStateFlow(BranchSortOption.ROUTE)
    private val isSyncing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val branchesFlow = combine(searchText, selectedAccount, sortOption) { search, account, sort ->
        Triple(search, account, sort)
    }.flatMapLatest { (search, account, sort) ->
        branchRepository.observeBranches(
            searchText = search,
            accountFilter = account,
            sortOption = sort
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
        settingsRepository.lastSyncAtFlow
    ) { inputs, branches, lastSyncAt ->
        BranchListUiState(
            searchText = inputs.searchText,
            selectedAccount = inputs.selectedAccount,
            sortOption = inputs.sortOption,
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
    }

    fun updateAccountFilter(value: String?) {
        selectedAccount.value = value
    }

    fun updateSortOption(value: BranchSortOption) {
        sortOption.value = value
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
            settingsRepository: SettingsRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BranchListViewModel(branchRepository, settingsRepository) as T
                }
            }
        }
    }
}
