package com.dateroem.woodsales.ui.ai

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

sealed interface UnassignedTransactionsUiState {
    data object Loading : UnassignedTransactionsUiState
    data class Loaded(val transactions: List<JsonMap>) : UnassignedTransactionsUiState
    data class Error(val message: String) : UnassignedTransactionsUiState
}

// "you ask any unassigned transactions? it will provide them and when you
// click them it will lead you to being taken to the assign transaction" -
// a plain read-only browse (unlike Transactions' own claim-queue flow,
// which mutates who owns which row); tapping one reuses the existing
// assign_detail screen.
class UnassignedTransactionsViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<UnassignedTransactionsUiState>(UnassignedTransactionsUiState.Loading)
    val uiState: StateFlow<UnassignedTransactionsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = UnassignedTransactionsUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                UnassignedTransactionsUiState.Loaded(api.unassignedTransactions())
            } catch (e: Exception) {
                UnassignedTransactionsUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UnassignedTransactionsViewModel(api) as T
    }
}
