package com.dateroem.woodsales.ui.expenses

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

sealed interface ExpenseCategoriesUiState {
    data object Loading : ExpenseCategoriesUiState
    data class Loaded(val totals: JsonMap) : ExpenseCategoriesUiState
    data class Error(val message: String) : ExpenseCategoriesUiState
}

class ExpenseCategoriesViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<ExpenseCategoriesUiState>(ExpenseCategoriesUiState.Loading)
    val uiState: StateFlow<ExpenseCategoriesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = ExpenseCategoriesUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                ExpenseCategoriesUiState.Loaded(api.expenseCategoryTotals(tripId))
            } catch (e: Exception) {
                ExpenseCategoriesUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExpenseCategoriesViewModel(api, tripId) as T
    }
}
