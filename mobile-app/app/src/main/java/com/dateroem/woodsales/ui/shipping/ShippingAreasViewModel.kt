package com.dateroem.woodsales.ui.shipping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.ShippingArea
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface ShippingAreasUiState {
    data object Loading : ShippingAreasUiState
    data class Loaded(val areas: List<ShippingArea>) : ShippingAreasUiState
    data class Error(val message: String) : ShippingAreasUiState
}

// "create a place to edit and add or modify the existing picking areas so
// that this is added also in the heavy order area" - a management screen
// for DELIVERY_AREAS, the same reference table the website's checkout
// dropdown reads from, reachable from the drawer and from Heavy Orders.
class ShippingAreasViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<ShippingAreasUiState>(ShippingAreasUiState.Loading)
    val uiState: StateFlow<ShippingAreasUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = ShippingAreasUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                ShippingAreasUiState.Loaded(api.listShippingAreas())
            } catch (e: Exception) {
                ShippingAreasUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShippingAreasViewModel(api) as T
    }
}
