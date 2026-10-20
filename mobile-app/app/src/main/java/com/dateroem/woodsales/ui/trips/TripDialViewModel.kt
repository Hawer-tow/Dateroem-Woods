package com.dateroem.woodsales.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.data.session.ActiveTripStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Backs the bottom wood-dial: "one can rotate from trip number 1 to 6
// since that is the maximum or none". Positions 1-6 are just this trip
// list's creation order (Trip_ID ascending), not a stored field - the
// business rule caps the system at 6 trips, so the dial always has exactly
// 7 slots (None + 1-6), fading out slots beyond however many exist so far.
class TripDialViewModel(private val api: ApiService, val activeTripStore: ActiveTripStore) : ViewModel() {

    private val _trips = MutableStateFlow<List<JsonMap>>(emptyList())
    val trips: StateFlow<List<JsonMap>> = _trips.asStateFlow()

    fun loadTrips() {
        viewModelScope.launch {
            try {
                _trips.value = api.listTrips().sortedBy { (it["tripId"] as? Number)?.toInt() ?: 0 }
            } catch (e: Exception) {
                // Leave whatever was last loaded - the dial must never crash the app shell.
            }
        }
    }

    fun select(tripId: Int?) {
        viewModelScope.launch { activeTripStore.setActiveTrip(tripId) }
    }

    class Factory(private val api: ApiService, private val activeTripStore: ActiveTripStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripDialViewModel(api, activeTripStore) as T
    }
}
