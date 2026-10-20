package com.dateroem.woodsales.ui.bulkorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.CreateBulkOrderRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class BulkOrderFormState(
    val deliveredTo: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// "i want to allow the admin or staff to create a bulk order and its
// added into the system and if it fails its just deleted ... since the
// bulk goods communications are done offline and online but mostly
// offline" - opens the negotiation record.
class BulkOrderFormViewModel(private val api: ApiService, private val tripId: Int, private val actorUserId: Int) : ViewModel() {

    private val _state = MutableStateFlow(BulkOrderFormState())
    val state: StateFlow<BulkOrderFormState> = _state.asStateFlow()

    fun updateDeliveredTo(v: String) { _state.value = _state.value.copy(deliveredTo = v) }

    fun submit() {
        val s = _state.value
        if (s.deliveredTo.isBlank()) {
            _state.value = s.copy(error = "Enter the company name")
            return
        }
        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                api.createBulkOrder(CreateBulkOrderRequest(s.deliveredTo.trim(), tripId), actorUserId)
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { return it.groupValues[1] }
            return friendlyError(e, "Something went wrong. Please try again.")
        }
        return friendlyError(e, "Couldn't create this order")
    }

    class Factory(
        private val api: ApiService,
        private val tripId: Int,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BulkOrderFormViewModel(api, tripId, actorUserId) as T
    }
}
