package com.dateroem.woodsales.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.CreateTripRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class TripFormState(
    val startDate: String = "",
    val productDescription: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    // "you can never create a new trip before you finalize every detail in
    // the other trip" - set when another trip is still open; the form is
    // replaced by a pointer to that trip.
    val checking: Boolean = true,
    val openTripId: Int? = null
)

class TripFormViewModel(private val api: ApiService) : ViewModel() {

    private val _state = MutableStateFlow(TripFormState())
    val state: StateFlow<TripFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val openId = try {
                ((api.openTrip()["openTrip"] as? Map<*, *>)?.get("tripId") as? Number)?.toInt()
            } catch (e: Exception) {
                null // can't tell - let the server decide when saving
            }
            _state.value = _state.value.copy(checking = false, openTripId = openId)
        }
    }

    fun updateStartDate(v: String) { _state.value = _state.value.copy(startDate = v) }
    fun updateProductDescription(v: String) { _state.value = _state.value.copy(productDescription = v) }

    fun submit() {
        val s = _state.value
        if (s.startDate.isBlank()) {
            _state.value = s.copy(error = "Pick a start date")
            return
        }
        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                api.createTrip(CreateTripRequest(s.startDate.trim(), s.productDescription.trim().ifBlank { null }))
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            // The server's own reason ({"detail": "..."}) beats a raw JSON dump.
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body.orEmpty())?.let { return it.groupValues[1] }
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't create this trip")
    }

    class Factory(private val api: ApiService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TripFormViewModel(api) as T
    }
}
