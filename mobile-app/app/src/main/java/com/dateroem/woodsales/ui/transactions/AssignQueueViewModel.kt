package com.dateroem.woodsales.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface AssignQueueUiState {
    data object Loading : AssignQueueUiState
    data class Loaded(val claimed: List<JsonMap>) : AssignQueueUiState
    data class Error(val message: String) : AssignQueueUiState
}

// Admin-only working set - business rules: "if the FINANCIAL_TRANSACTION
// table has 14 transactions not assigned and another user logs in it can
// reduce one's load to 7 for each".
class AssignQueueViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<AssignQueueUiState>(AssignQueueUiState.Loading)
    val uiState: StateFlow<AssignQueueUiState> = _uiState.asStateFlow()

    private val _claiming = MutableStateFlow(false)
    val claiming: StateFlow<Boolean> = _claiming.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = AssignQueueUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                AssignQueueUiState.Loaded(api.claimedTransactions(actorUserId))
            } catch (e: Exception) {
                AssignQueueUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun claimMore() {
        _claiming.value = true
        viewModelScope.launch {
            try {
                api.claimTransactions(7, actorUserId)
                refresh()
            } catch (e: Exception) {
                _uiState.value = AssignQueueUiState.Error(friendlyError(e, "Couldn't claim"))
            } finally {
                _claiming.value = false
            }
        }
    }

    fun release(financialTransactionId: Int) {
        viewModelScope.launch {
            try {
                api.releaseTransaction(financialTransactionId)
                refresh()
            } catch (_: Exception) {
                // Non-critical - just leave it claimed if the release call fails.
            }
        }
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AssignQueueViewModel(api, actorUserId) as T
    }
}
