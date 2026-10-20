package com.dateroem.woodsales.ui.finance

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

sealed interface FinanceOverviewUiState {
    data object Loading : FinanceOverviewUiState
    data class Loaded(val loans: List<JsonMap>, val capital: List<JsonMap>) : FinanceOverviewUiState
    data class Error(val message: String) : FinanceOverviewUiState
}

// "the loans and finance page (only of the admin/Superadmins) ... here one
// can view the loans capital as well as LOAN_REPAYMENTS table and assign a
// transaction to this area."
class FinanceOverviewViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<FinanceOverviewUiState>(FinanceOverviewUiState.Loading)
    val uiState: StateFlow<FinanceOverviewUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = FinanceOverviewUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                FinanceOverviewUiState.Loaded(api.tripLoans(tripId), api.tripCapital(tripId))
            } catch (e: Exception) {
                FinanceOverviewUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FinanceOverviewViewModel(api, tripId) as T
    }
}
