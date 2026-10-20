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

sealed interface RefundsUiState {
    data object Loading : RefundsUiState
    data class Loaded(val refunds: List<JsonMap>) : RefundsUiState
    data class Error(val message: String) : RefundsUiState
}

// "when one drops a product ... where exactly do i go ... still cannot see
// it" - every dropped order still owing its customer a refund
// (ORDERS.Refunded_Amount set, Financial_Transaction_ID still null), ready
// to be paid out via Send Money (ExpenseTarget.ORDER_REFUND).
class RefundsViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<RefundsUiState>(RefundsUiState.Loading)
    val uiState: StateFlow<RefundsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value !is RefundsUiState.Loaded) _uiState.value = RefundsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                RefundsUiState.Loaded(api.pendingRefunds())
            } catch (e: Exception) {
                RefundsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = RefundsViewModel(api) as T
    }
}
