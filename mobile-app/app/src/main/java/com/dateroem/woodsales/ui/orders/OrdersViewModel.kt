package com.dateroem.woodsales.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.AssignBatchRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

sealed interface OrdersUiState {
    data object Loading : OrdersUiState
    data class Loaded(val groups: List<JsonMap>) : OrdersUiState
    data class Error(val message: String) : OrdersUiState
}

// "some of these orders may be from the same person so how do we collect
// them in a single group so that an admin can gauge the goods and then
// check if there is a bulk order or light or combined" - the pending pool
// gathered per customer and delivery area; the same data the desktop's
// Insights > Orders tab shows, drawn as cards instead of table rows.
class OrdersViewModel(private val api: ApiService, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<OrdersUiState>(OrdersUiState.Loading)
    val uiState: StateFlow<OrdersUiState> = _uiState.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value !is OrdersUiState.Loaded) _uiState.value = OrdersUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                OrdersUiState.Loaded(api.pendingOrderGroups())
            } catch (e: Exception) {
                OrdersUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // "if a new order comes from that user ... one can still add it in the
    // bunch if the staff deems worth it" - the customer's new orders onto
    // the batch they already have (same trip, vehicle and delivery date).
    fun addToBatch(batch: JsonMap, lightDeliveryIds: List<Int>) {
        if (lightDeliveryIds.isEmpty()) return
        _working.value = true
        viewModelScope.launch {
            try {
                val isBulk = batch.text("kind") == "bulk"
                val targetId = batch.int("targetId")
                api.assignOrdersBatch(
                    AssignBatchRequest(
                        lightDeliveryIds = lightDeliveryIds,
                        tripId = batch.int("tripId"),
                        vehicleId = batch.int("vehicleId"),
                        estimatedDeliveryDate = batch.date("date"),
                        bulkDeliveryId = if (isBulk) targetId else null,
                        parentLightDeliveryId = if (isBulk) null else targetId
                    ),
                    actorUserId
                )
                _message.value = "Added ${lightDeliveryIds.size} order(s) to the batch"
                refresh()
            } catch (e: Exception) {
                _message.value = friendlyError(e, "Couldn't add to the batch")
            } finally {
                _working.value = false
            }
        }
    }

    class Factory(private val api: ApiService, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = OrdersViewModel(api, actorUserId) as T
    }
}
