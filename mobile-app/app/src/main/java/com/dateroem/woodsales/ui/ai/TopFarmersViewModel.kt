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

sealed interface TopFarmersUiState {
    data object Loading : TopFarmersUiState
    data class Loaded(val farmers: List<JsonMap>) : TopFarmersUiState
    data class Error(val message: String) : TopFarmersUiState
}

// "check a certain farmer and provide info that this farmer tops on the
// list as one of the farmers whom you bought most trees from" - ranked
// by total trees supplied across every trip.
class TopFarmersViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<TopFarmersUiState>(TopFarmersUiState.Loading)
    val uiState: StateFlow<TopFarmersUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        _uiState.value = TopFarmersUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TopFarmersUiState.Loaded(api.topFarmerSuppliers())
            } catch (e: Exception) {
                TopFarmersUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TopFarmersViewModel(api) as T
    }
}
