package com.branchphotovault.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.branchphotovault.data.repository.BranchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddBranchUiState(
    val account: String = "",
    val branchCode: String = "",
    val shopName: String = "",
    val routeText: String = "",
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val createdAccount: String? = null,
    val createdBranchCode: String? = null
)

class AddBranchViewModel(
    private val branchRepository: BranchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBranchUiState())
    val uiState: StateFlow<AddBranchUiState> = _uiState.asStateFlow()

    fun updateAccount(value: String) {
        if (value in ACCOUNTS) {
            _uiState.update { it.copy(account = value) }
        }
    }

    fun updateBranchCode(value: String) {
        _uiState.update { it.copy(branchCode = value.uppercase()) }
    }

    fun updateShopName(value: String) {
        _uiState.update { it.copy(shopName = value) }
    }

    fun updateRouteText(value: String) {
        _uiState.update { current ->
            current.copy(routeText = value.filter { it.isDigit() })
        }
    }

    fun submit() {
        val snapshot = _uiState.value
        if (snapshot.account !in ACCOUNTS || snapshot.branchCode.isBlank() || snapshot.shopName.isBlank()) {
            _uiState.update { it.copy(message = "Choose an Account, then enter branch code and shop name.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, message = null) }
            try {
                val route = snapshot.routeText.toIntOrNull()
                val branch = branchRepository.addBranch(
                    account = snapshot.account,
                    branchCode = snapshot.branchCode,
                    shopName = snapshot.shopName,
                    currentRoute = route
                )
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = "Branch saved.",
                        createdAccount = branch.account,
                        createdBranchCode = branch.branchCode
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        message = error.message ?: "Unable to add branch."
                    )
                }
            }
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(createdAccount = null, createdBranchCode = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        val ACCOUNTS = listOf(
            "7-ELEVEN",
            "BIGCMINI",
            "CJ MORE",
            "JIFFY",
            "LAWSON",
            "LOTUS'S GO FRESH",
            "TOPS DAILY"
        )

        fun provideFactory(branchRepository: BranchRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AddBranchViewModel(branchRepository) as T
                }
            }
        }
    }
}
