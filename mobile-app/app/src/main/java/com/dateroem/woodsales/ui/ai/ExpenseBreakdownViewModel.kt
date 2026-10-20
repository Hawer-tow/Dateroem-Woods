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

sealed interface ExpenseBreakdownUiState {
    data object Loading : ExpenseBreakdownUiState
    data class Loaded(val rows: List<JsonMap>) : ExpenseBreakdownUiState
    data class Error(val message: String) : ExpenseBreakdownUiState
}

// "for the expense it can scan for the expense provide information on
// the most expensive expense and observe even from other trips ... more
// cost were incurred in the transport section compared to the other
// trip" - one row per (trip, category), ranked highest first.
class ExpenseBreakdownViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpenseBreakdownUiState>(ExpenseBreakdownUiState.Loading)
    val uiState: StateFlow<ExpenseBreakdownUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = ExpenseBreakdownUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                ExpenseBreakdownUiState.Loaded(api.expenseBreakdown())
            } catch (e: Exception) {
                ExpenseBreakdownUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpenseBreakdownViewModel(api) as T
    }
}
