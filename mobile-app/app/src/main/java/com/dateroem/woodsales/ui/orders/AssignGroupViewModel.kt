package com.dateroem.woodsales.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.AssignBatchRequest
import com.dateroem.woodsales.data.model.CreateBulkOrderRequest
import com.dateroem.woodsales.data.model.CreateLightDeliveryRequest
import com.dateroem.woodsales.data.model.DropOrderRequest
import com.dateroem.woodsales.data.model.Vehicle
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class AssignGroupState(
    val loading: Boolean = true,
    val error: String? = null,
    val group: JsonMap? = null,
    // Only trips that are still open can take orders - a finalized trip is over.
    val trips: List<JsonMap> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val step: Int = 0,
    val trip: JsonMap? = null,
    val loadingDeliveries: Boolean = false,
    // The trip's existing deliveries of the chosen kind, as ready-to-show rows.
    val deliveries: List<DeliveryChoice> = emptyList(),
    val choice: DeliveryChoice? = null,
    val date: String = "",
    val vehicleId: Int? = null,
    val deliveredTo: String = "",
    // "allow one to name a delivery when assigning a new one ... a name
    // delivery maasai allows quick identification" - light-only (bulk
    // already has deliveredTo for the same purpose).
    val deliveryName: String = "",
    val ticked: Set<Int> = emptySet(),
    val submitting: Boolean = false,
    val done: Boolean = false,
    val droppingOrderId: Int? = null,
    val dropError: String? = null,
    val lastDropRefund: Double? = null
)

// isNew = "create a new delivery"; otherwise id is the LIGHT_DELIVERIES id
// (light) or BULK_DELIVERY id (bulk) to join.
data class DeliveryChoice(val isNew: Boolean, val id: Int?, val title: String, val subtitle: String, val date: String?, val vehicleId: Int?)

// "a pops that is one can select a predefined delivery or create a new one
// and add that group in that delivery" - trip -> delivery (existing or new)
// -> tick which of the group's orders go on it -> confirm. Same steps as the
// desktop wizard, one screen at a time.
class AssignGroupViewModel(
    private val api: ApiService,
    private val actorUserId: Int,
    private val customerId: Int,
    private val areaId: Int,
    val kind: String // "light" | "bulk"
) : ViewModel() {

    private val _state = MutableStateFlow(AssignGroupState())
    val state: StateFlow<AssignGroupState> = _state.asStateFlow()

    // A delivery already created by an attempt that then failed to assign is
    // reused on retry instead of making a second empty one.
    private var createdId: Int? = null

    init {
        viewModelScope.launch {
            try {
                val group = api.pendingOrderGroups().firstOrNull { it.int("customerId") == customerId && (it.int("areaId") ?: -1) == areaId }
                    ?: throw IllegalStateException("These orders are no longer pending - go back and refresh")
                val trips = api.listTrips().filter { it["endDate"] == null }
                val vehicles = try { api.listVehicles() } catch (e: Exception) { emptyList() }
                _state.value = _state.value.copy(
                    loading = false,
                    group = group,
                    trips = trips,
                    vehicles = vehicles,
                    deliveredTo = group.text("customer") ?: "",
                    ticked = group.maps("orders").mapNotNull { it.int("lightDeliveryId") }.toSet()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = describe(e))
            }
        }
    }

    fun pickTrip(trip: JsonMap) {
        val tripId = trip.int("tripId") ?: return
        _state.value = _state.value.copy(step = 1, trip = trip, loadingDeliveries = true, error = null, date = trip.date("endDate") ?: "")
        viewModelScope.launch {
            try {
                val data = api.tripDeliveries(tripId)
                val light = data.maps("light")
                val choices = mutableListOf(
                    DeliveryChoice(true, null, "+  Create a new $kind delivery", "Start a fresh one on this trip", null, null)
                )
                if (kind == "light") {
                    // "if a delivery was completed ... dont allow it to be
                    // assigned new orders ... you either create a new one
                    // or use one still active" - a delivered head has
                    // already gone out (the backend rejects it too either
                    // way, but it shouldn't even be offered here).
                    light.filter {
                        it.int("parentLightDeliveryId") == null && it.int("bulkDeliveryId") == null &&
                            it.text("status") != "DELIVERED"
                    }.forEach { head ->
                        val kids = light.count { it.int("parentLightDeliveryId") == head.int("lightDeliveryId") && it.text("status") != "DELIVERED" }
                        val own = if ((head["total"] as? Number)?.toInt() ?: 0 > 0 && head.text("status") != "DELIVERED") 1 else 0
                        val name = head.text("deliveryName")
                        choices.add(
                            DeliveryChoice(
                                false, head.int("lightDeliveryId"),
                                if (name != null) "Light delivery #${head.int("lightDeliveryId")} - $name" else "Light delivery #${head.int("lightDeliveryId")}",
                                "${kids + own} order(s) · ${head.date("estimatedDeliveryDate") ?: "no date"} · ${head.text("status") ?: ""}",
                                head.date("estimatedDeliveryDate"), head.int("vehicleId")
                            )
                        )
                    }
                } else {
                    // Same rule for bulk: already delivered (deliveredAt
                    // set) isn't a valid target for a new side quest.
                    data.maps("bulk").filter { it.text("deliveredAt") == null }.forEach { bulk ->
                        val kids = light.count { it.int("bulkDeliveryId") == bulk.int("bulkDeliveryId") && it.text("status") != "DELIVERED" }
                        choices.add(
                            DeliveryChoice(
                                false, bulk.int("bulkDeliveryId"),
                                "Bulk delivery #${bulk.int("bulkDeliveryId")}",
                                "${bulk.text("deliveredTo") ?: ""} · $kids side quest(s)",
                                null, null
                            )
                        )
                    }
                }
                // Default to an existing delivery when there is one.
                val first = if (choices.size > 1) choices[1] else choices[0]
                _state.value = _state.value.copy(loadingDeliveries = false, deliveries = choices)
                choose(first)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingDeliveries = false, error = describe(e))
            }
        }
    }

    fun choose(choice: DeliveryChoice) {
        val current = _state.value
        _state.value = current.copy(
            choice = choice,
            date = choice.date ?: current.trip?.date("endDate") ?: current.date,
            vehicleId = if (choice.isNew) current.vehicleId else (choice.vehicleId ?: current.vehicleId)
        )
    }

    fun backToTrips() { _state.value = _state.value.copy(step = 0, error = null) }
    fun setDate(v: String) { _state.value = _state.value.copy(date = v) }
    fun setVehicle(id: Int?) { _state.value = _state.value.copy(vehicleId = id) }
    fun setDeliveredTo(v: String) { _state.value = _state.value.copy(deliveredTo = v) }
    fun setDeliveryName(v: String) { _state.value = _state.value.copy(deliveryName = v) }
    fun toggle(id: Int) {
        val t = _state.value.ticked
        _state.value = _state.value.copy(ticked = if (id in t) t - id else t + id)
    }

    fun confirm() {
        val s = _state.value
        val choice = s.choice ?: run { _state.value = s.copy(error = "Pick a delivery, or create a new one"); return }
        val tripId = s.trip?.int("tripId") ?: return
        if (s.ticked.isEmpty()) { _state.value = s.copy(error = "Tick at least one order"); return }
        if (s.date.isBlank()) { _state.value = s.copy(error = "Pick the delivery date - it's what the customer will see"); return }
        if (choice.isNew && kind == "bulk" && s.deliveredTo.isBlank()) { _state.value = s.copy(error = "Say who the bulk delivery is going to"); return }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val targetId: Int? = if (choice.isNew) {
                    if (createdId == null) {
                        createdId = if (kind == "light") {
                            api.createLightDelivery(
                                CreateLightDeliveryRequest(
                                    tripId, s.vehicleId, s.group?.int("areaId"), s.date, s.deliveryName.trim().ifBlank { null }
                                ), actorUserId
                            ).int("lightDeliveryId")
                        } else {
                            api.createBulkOrder(CreateBulkOrderRequest(s.deliveredTo.trim(), tripId), actorUserId).int("bulkDeliveryId")
                        }
                    }
                    createdId
                } else choice.id

                api.assignOrdersBatch(
                    AssignBatchRequest(
                        lightDeliveryIds = s.ticked.toList(),
                        tripId = tripId,
                        vehicleId = s.vehicleId,
                        estimatedDeliveryDate = s.date,
                        bulkDeliveryId = if (kind == "bulk") targetId else null,
                        parentLightDeliveryId = if (kind == "light") targetId else null
                    ),
                    actorUserId
                )
                _state.value = _state.value.copy(submitting = false, done = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describe(e))
            }
        }
    }

    // "allow a user to drop the product if it has not yet been assigned a
    // trip which will allow one to get refunded the money paid for that
    // product but a cost is reduced from the initially sent by the
    // admins who can only do this and after it is dropped ... the stock
    // ... will go to 24" - only reachable from here because every order
    // on this screen is, by definition, still pending (never yet put on
    // a trip); the backend re-checks that itself too.
    fun dropOrder(orderId: Int, cancellationCost: Double) {
        val s = _state.value
        _state.value = s.copy(droppingOrderId = orderId, dropError = null)
        viewModelScope.launch {
            try {
                val result = api.dropOrder(orderId, DropOrderRequest(cancellationCost))
                val current = _state.value
                val group = current.group
                val updatedGroup = group?.let {
                    it + ("orders" to it.maps("orders").filterNot { o -> o.int("orderId") == orderId })
                }
                val droppedLightId = group?.maps("orders")?.firstOrNull { it.int("orderId") == orderId }?.int("lightDeliveryId")
                _state.value = current.copy(
                    droppingOrderId = null,
                    group = updatedGroup,
                    ticked = if (droppedLightId != null) current.ticked - droppedLightId else current.ticked,
                    lastDropRefund = (result["refundedAmount"] as? Number)?.toDouble()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(droppingOrderId = null, dropError = describe(e))
            }
        }
    }

    fun dismissDropRefund() { _state.value = _state.value.copy(lastDropRefund = null) }

    private fun describe(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { return it.groupValues[1] }
            return friendlyError(e, "Something went wrong. Please try again.")
        }
        return friendlyError(e, "Something went wrong")
    }

    class Factory(
        private val api: ApiService, private val actorUserId: Int,
        private val customerId: Int, private val areaId: Int, private val kind: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AssignGroupViewModel(api, actorUserId, customerId, areaId, kind) as T
    }
}
