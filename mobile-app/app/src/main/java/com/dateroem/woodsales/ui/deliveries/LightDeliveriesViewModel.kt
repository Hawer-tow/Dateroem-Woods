package com.dateroem.woodsales.ui.deliveries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.DeliverLightDeliveryRequest
import com.dateroem.woodsales.data.model.SettleRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface LightDeliveriesUiState {
    data object Loading : LightDeliveriesUiState
    data class Loaded(val deliveries: List<JsonMap>) : LightDeliveriesUiState
    data class Error(val message: String) : LightDeliveriesUiState
}

// "in a heavy or light one can see the level details of each delivery" -
// and, like the desktop, a delivery holds one or more orders: each can be
// marked delivered on its own, and finalising the delivery sends whatever
// wasn't delivered back to the pending list as priority.
class LightDeliveriesViewModel(
    private val api: ApiService,
    private val tripId: Int,
    private val actorUserId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow<LightDeliveriesUiState>(LightDeliveriesUiState.Loading)
    val uiState: StateFlow<LightDeliveriesUiState> = _uiState.asStateFlow()

    private val _updatingIds = MutableStateFlow<Set<Int>>(emptySet())
    val updatingIds: StateFlow<Set<Int>> = _updatingIds.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value !is LightDeliveriesUiState.Loaded) _uiState.value = LightDeliveriesUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                LightDeliveriesUiState.Loaded(api.listLightDeliveries(tripId))
            } catch (e: Exception) {
                LightDeliveriesUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun markDelivered(lightDeliveryId: Int, signature: String? = null, receivedBy: String? = null) {
        _updatingIds.value = _updatingIds.value + lightDeliveryId
        viewModelScope.launch {
            try {
                api.deliverLightDelivery(lightDeliveryId, DeliverLightDeliveryRequest(signature, receivedBy))
                refresh()
            } catch (e: Exception) {
                _message.value = friendlyError(e, "Couldn't update the order")
            } finally {
                _updatingIds.value = _updatingIds.value - lightDeliveryId
            }
        }
    }

    // Which orders arrived and which didn't: the delivered are closed out,
    // the rest are cut loose and go back to the pending list flagged as
    // priority (with the reason). closeDeliveryId also marks the delivery
    // itself delivered (a container with nothing left on it).
    fun settle(delivered: List<Int>, undelivered: List<Int>, reason: String?, closeDeliveryId: Int?) {
        val workingIds = (delivered + undelivered + listOfNotNull(closeDeliveryId)).toSet()
        _updatingIds.value = _updatingIds.value + workingIds
        viewModelScope.launch {
            try {
                val closing = if (closeDeliveryId != null && closeDeliveryId !in delivered && closeDeliveryId !in undelivered) {
                    delivered + closeDeliveryId
                } else delivered
                api.settleLightOrders(SettleRequest(closing, undelivered, reason?.takeIf { it.isNotBlank() }), actorUserId)
                refresh()
            } catch (e: Exception) {
                _message.value = friendlyError(e, "Couldn't save the delivery result")
            } finally {
                _updatingIds.value = _updatingIds.value - workingIds
            }
        }
    }

    class Factory(private val api: ApiService, private val tripId: Int, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LightDeliveriesViewModel(api, tripId, actorUserId) as T
    }
}
