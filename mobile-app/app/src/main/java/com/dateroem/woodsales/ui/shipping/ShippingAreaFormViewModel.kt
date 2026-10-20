package com.dateroem.woodsales.ui.shipping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.ShippingArea
import com.dateroem.woodsales.data.model.ShippingAreaRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

data class ShippingAreaFormState(
    val loading: Boolean = false,
    val city: String = "",
    val areaName: String = "",
    val description: String = "",
    val isActive: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)

// Create-or-edit form for one DELIVERY_AREAS row - "a place to edit and
// add or modify the existing picking areas". No delete: Is_Active already
// gives staff a clean way to take an area out of rotation without losing
// the record entirely (and the website's checkout dropdown only ever
// shows active ones).
class ShippingAreaFormViewModel(
    private val api: ApiService,
    private val editingAreaId: Int?
) : ViewModel() {

    private val _state = MutableStateFlow(ShippingAreaFormState(loading = editingAreaId != null))
    val state: StateFlow<ShippingAreaFormState> = _state.asStateFlow()

    init {
        editingAreaId?.let { id ->
            viewModelScope.launch {
                try {
                    applyLoadedArea(api.getShippingArea(id))
                } catch (e: Exception) {
                    _state.value = _state.value.copy(loading = false, error = friendlyError(e, "Couldn't load this area"))
                }
            }
        }
    }

    private fun applyLoadedArea(area: ShippingArea) {
        _state.value = _state.value.copy(
            loading = false,
            city = area.city.orEmpty(),
            areaName = area.areaName.orEmpty(),
            description = area.description.orEmpty(),
            isActive = area.isActive != false
        )
    }

    fun updateCity(v: String) { _state.value = _state.value.copy(city = v) }
    fun updateAreaName(v: String) { _state.value = _state.value.copy(areaName = v) }
    fun updateDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun updateIsActive(v: Boolean) { _state.value = _state.value.copy(isActive = v) }

    fun submit() {
        val s = _state.value
        if (s.city.isBlank()) {
            _state.value = s.copy(error = "City is required")
            return
        }
        if (s.areaName.isBlank()) {
            _state.value = s.copy(error = "Area name is required")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val request = ShippingAreaRequest(
                    city = s.city.trim(),
                    areaName = s.areaName.trim(),
                    description = s.description.trim().ifBlank { null },
                    isActive = s.isActive
                )
                if (editingAreaId != null) {
                    api.updateShippingArea(editingAreaId, request)
                } else {
                    api.createShippingArea(request)
                }
                _state.value = _state.value.copy(submitting = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    private fun describeError(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string()
            return friendlyError(e, "Something went wrong. Please try again.", body)
        }
        return friendlyError(e, "Couldn't save this delivery area")
    }

    class Factory(
        private val api: ApiService,
        private val editingAreaId: Int?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ShippingAreaFormViewModel(api, editingAreaId) as T
    }
}
