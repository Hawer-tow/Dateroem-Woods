package com.dateroem.woodsales.ui.bulkorders

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

sealed interface BulkOrdersUiState {
    data object Loading : BulkOrdersUiState
    data class Loaded(val orders: List<JsonMap>) : BulkOrdersUiState
    data class Error(val message: String) : BulkOrdersUiState
}

// "the heavy delivery orders search and finalization ... i want to allow
// the admin or staff to create a bulk order and its added into the system"
class BulkOrdersViewModel(private val api: ApiService, private val tripId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<BulkOrdersUiState>(BulkOrdersUiState.Loading)
    val uiState: StateFlow<BulkOrdersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = BulkOrdersUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                BulkOrdersUiState.Loaded(api.listBulkOrders(tripId))
            } catch (e: Exception) {
                BulkOrdersUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BulkOrdersViewModel(api, tripId) as T
    }
}
