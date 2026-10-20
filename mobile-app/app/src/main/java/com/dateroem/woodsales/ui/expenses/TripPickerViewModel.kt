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

sealed interface TripPickerUiState {
    data object Loading : TripPickerUiState
    data class Loaded(val trips: List<JsonMap>) : TripPickerUiState
    data class Error(val message: String) : TripPickerUiState
}

// Business rules: "Pick a trip first to see its expense categories" -
// expenses/transactions are recorded per trip, so this is the entry point
// before the category totals screen.
class TripPickerViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TripPickerUiState>(TripPickerUiState.Loading)
    val uiState: StateFlow<TripPickerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = TripPickerUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TripPickerUiState.Loaded(api.listTrips())
            } catch (e: Exception) {
                TripPickerUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripPickerViewModel(api) as T
    }
}
