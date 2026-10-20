package com.dateroem.woodsales.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dateroem.woodsales.data.model.SendMoneyRequest
import com.dateroem.woodsales.data.network.ApiService

// "Send Money" (Daraja B2C) - business owner correction: "expenses are
// payments done to the other partners ... i dont think its an stk push
// but rather a send money option". Always pays a specific, already-known
// target (categoryLabel/targetRowId are fixed context, not user-picked) -
// admin-only, enforced server-side. The amount and recipient are looked up
// from the entry and the M-Pesa receipt fills itself in (see
// PayoutController) - nobody types one.
class SendMoneyViewModel(
    api: ApiService,
    target: String,
    targetRowId: Int,
    val categoryLabel: String,
    prefilledAmount: Double?,
    actorUserId: Int
) : ViewModel() {

    val flow = PayoutController(
        api = api,
        scope = viewModelScope,
        actorUserId = actorUserId,
        recipientTarget = target,
        recipientRowId = targetRowId,
        prefilledAmount = prefilledAmount
    ) { amount, phone -> api.sendMoney(SendMoneyRequest(amount, phone, target, targetRowId), actorUserId) }

    class Factory(
        private val api: ApiService,
        private val target: String,
        private val targetRowId: Int,
        private val categoryLabel: String,
        private val prefilledAmount: Double?,
        private val actorUserId: Int
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SendMoneyViewModel(api, target, targetRowId, categoryLabel, prefilledAmount, actorUserId) as T
    }
}
