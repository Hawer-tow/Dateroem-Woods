package com.dateroem.woodsales.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.ConfirmStkPushRequest
import com.dateroem.woodsales.data.model.StkPushRequest
import com.dateroem.woodsales.data.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

enum class StkPushStep { SEND, CONFIRM, DONE }

data class StkPushState(
    val step: StkPushStep = StkPushStep.SEND,
    val amount: String = "",
    val phoneNumber: String = "",
    val referenceId: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val financialTransactionId: Int? = null,
    val darajaInitiated: Boolean = false,
    val confirmStatus: String? = null
)

// STK pulls money FROM a customer - business owner correction: "the stk
// option will only happen to users paying for a product in the product
// payment ... and an exempt for bulk goods also in transaction section".
// Open to any signed-in user, no pre-targeting to an expense category -
// expenses are paid OUT to a partner via SendMoneyScreen instead.
class StkPushViewModel(
    private val api: ApiService,
    private val actorUserId: Int,
    prefilledAmount: Double? = null
) : ViewModel() {

    private val _state = MutableStateFlow(StkPushState(amount = prefilledAmount?.toString().orEmpty()))
    val state: StateFlow<StkPushState> = _state.asStateFlow()

    fun updateAmount(v: String) { _state.value = _state.value.copy(amount = v) }
    fun updatePhoneNumber(v: String) { _state.value = _state.value.copy(phoneNumber = v) }
    fun updateReferenceId(v: String) { _state.value = _state.value.copy(referenceId = v) }

    fun send() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        if (amount == null) {
            _state.value = s.copy(error = "Enter a valid amount")
            return
        }

        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val response = api.stkPush(StkPushRequest(amount, s.phoneNumber.trim().ifBlank { null }), actorUserId)
                val id = (response["financialTransactionId"] as? Number)?.toInt()
                val darajaInitiated = response["darajaInitiated"] as? Boolean ?: false
                _state.value = _state.value.copy(
                    submitting = false,
                    step = StkPushStep.CONFIRM,
                    financialTransactionId = id,
                    darajaInitiated = darajaInitiated
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describeError(e))
            }
        }
    }

    fun confirm() {
        val s = _state.value
        val id = s.financialTransactionId ?: return
        if (s.referenceId.isBlank()) {
            _state.value = s.copy(error = "Enter the M-Pesa receipt code")
            return
        }
        _state.value = s.copy(submitting = true, error = null)
        viewModelScope.launch {
            try {
                val response = api.confirmStkPush(id, ConfirmStkPushRequest(s.referenceId.trim()), actorUserId)
                _state.value = _state.value.copy(
                    submitting = false,
                    step = StkPushStep.DONE,
                    confirmStatus = response["status"] as? String
                )
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
        return friendlyError(e, "Something went wrong")
    }

    class Factory(
        private val api: ApiService,
        private val actorUserId: Int,
        private val prefilledAmount: Double? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StkPushViewModel(api, actorUserId, prefilledAmount) as T
    }
}
