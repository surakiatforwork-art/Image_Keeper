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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BranchListUiState(
    val searchText: String = "",
    val selectedAccounts: Set<String> = emptySet(),
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
        val selectedAccounts: Set<String>,
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
    private val initialAccounts: Set<String> =
        (savedStateHandle.get<ArrayList<String>>("account_filters")?.toSet()
            ?: browsePreferences.getStringSet("account_filters", null)?.toSet()
            ?: browsePreferences.getString("account_filter", null)?.let { setOf(it) }
            ?: emptySet())
    private val selectedAccounts = MutableStateFlow(initialAccounts)
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

    private data class BranchFilters(val search: String, val accounts: Set<String>, val sort: BranchSortOption, val withPhotos: Boolean)

    private val branchesFlow = combine(searchText, selectedAccounts, sortOption, onlyWithPhotos) { search, accounts, sort, withPhotos ->
        BranchFilters(search, accounts, sort, withPhotos)
    }.flatMapLatest { (search, accounts, sort, withPhotos) ->
        // SQL still handles search/sort/photo-count; account checkbox selections
        // are matched exactly (no substring LIKE or hand-built SQL).
        branchRepository.observeBranches(
            searchText = search,
            accountFilter = null,
            sortOption = sort,
            onlyWithPhotos = withPhotos
        ).map { branches ->
            if (accounts.isEmpty()) branches else branches.filter { it.account in accounts }
        }
    }

    private val inputsFlow = combine(
        searchText,
        selectedAccounts,
        sortOption,
        isSyncing,
        message
    ) { search, accounts, sort, syncing, userMessage ->
        InputsState(
            searchText = search,
            selectedAccounts = accounts,
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
            selectedAccounts = inputs.selectedAccounts,
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

    /**
     * Empty set means ALL accounts; checking a row starts or expands a
     * multi-account selection. Removing the last check returns to ALL.
     */
    fun toggleAccountFilter(account: String) {
        if (account.isBlank()) return
        val current = selectedAccounts.value
        val updated = if (account in current) current - account else current + account
        updateAccountFilters(updated)
    }

    fun clearAccountFilters() = updateAccountFilters(emptySet())

    private fun updateAccountFilters(accounts: Set<String>) {
        selectedAccounts.value = accounts
        savedStateHandle["account_filters"] = ArrayList(accounts.sorted())
        browsePreferences.edit()
            .putStringSet("account_filters", accounts.toSet())
            .remove("account_filter")
            .apply()
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
