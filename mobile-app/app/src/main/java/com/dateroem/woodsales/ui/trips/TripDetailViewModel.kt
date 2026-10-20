package com.dateroem.woodsales.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.FinalizeTripRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.dateroem.woodsales.ui.common.friendlyError

data class TripDetailData(
    val trip: JsonMap,
    val summary: JsonMap,
    val lightCount: Int,
    val bulkCount: Int,
    // "for the loan and financing it should be a total of the 2" - loans
    // plus capital, one figure, on this screen; the breakdown lives in
    // Loans & Finance itself now.
    val totalFinance: Double
)

sealed interface TripDetailUiState {
    data object Loading : TripDetailUiState
    data class Loaded(val data: TripDetailData) : TripDetailUiState
    data class Error(val message: String) : TripDetailUiState
}

// "one can click a trip and see the complex details of a trip e.g the
// number of deliveries ... the categories heavy light ... the permit
// associated with the trip" - "i should not have a financial filling area
// what i should see are the major details pertaining the trip" - the cash
// split editing and per-loan breakdown live in Loans & Finance / Capital
// now (see CapitalViewModel, LoansListViewModel); this screen just shows
// the trip's own operational details plus one combined finance total.
class TripDetailViewModel(private val api: ApiService, private val tripId: Int, private val actorUserId: Int) : ViewModel() {

    private val _uiState = MutableStateFlow<TripDetailUiState>(TripDetailUiState.Loading)
    val uiState: StateFlow<TripDetailUiState> = _uiState.asStateFlow()

    // "you can never create a new trip before you finalize every detail in
    // the other trip" - what's still open on this trip, shown before it can
    // be finalized.
    private val _finalization = MutableStateFlow<JsonMap?>(null)
    val finalization: StateFlow<JsonMap?> = _finalization.asStateFlow()
    private val _finalizing = MutableStateFlow(false)
    val finalizing: StateFlow<Boolean> = _finalizing.asStateFlow()
    private val _finalizeError = MutableStateFlow<String?>(null)
    val finalizeError: StateFlow<String?> = _finalizeError.asStateFlow()

    init {
        refresh()
    }

    fun openFinalize() {
        _finalizeError.value = null
        viewModelScope.launch {
            try {
                _finalization.value = api.tripFinalization(tripId)
            } catch (e: Exception) {
                _finalizeError.value = friendlyError(e, "Couldn't check the trip")
                _finalization.value = emptyMap()
            }
        }
    }

    fun dismissFinalize() {
        _finalization.value = null
        _finalizeError.value = null
    }

    fun finalizeTrip(dropUndelivered: Boolean) {
        _finalizing.value = true
        _finalizeError.value = null
        viewModelScope.launch {
            try {
                api.finalizeTrip(tripId, FinalizeTripRequest(dropUndelivered), actorUserId)
                _finalization.value = null
                refresh()
            } catch (e: Exception) {
                val body = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string().orEmpty()
                _finalizeError.value = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
                    ?: (friendlyError(e, "Couldn't finalize the trip"))
            } finally {
                _finalizing.value = false
            }
        }
    }

    fun refresh() {
        _uiState.value = TripDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                TripDetailUiState.Loaded(loadData())
            } catch (e: Exception) {
                TripDetailUiState.Error(friendlyError(e, "Couldn't load this right now"))
            }
        }
    }

    private suspend fun loadData(): TripDetailData {
        val trip = api.getTrip(tripId)
        val summary = api.tripSummary(tripId)
        val deliveries = api.tripDeliveries(tripId)
        val lightCount = (deliveries["lightCount"] as? Number)?.toInt() ?: 0
        val bulkCount = (deliveries["bulkCount"] as? Number)?.toInt() ?: 0

        val totalLoans = api.tripLoans(tripId).sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
        val totalCapital = api.tripCapital(tripId).sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

        return TripDetailData(trip, summary, lightCount, bulkCount, totalLoans + totalCapital)
    }

    class Factory(private val api: ApiService, private val tripId: Int, private val actorUserId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripDetailViewModel(api, tripId, actorUserId) as T
    }
}
