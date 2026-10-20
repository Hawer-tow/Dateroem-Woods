package com.dateroem.woodsales.ui.transactions

import com.dateroem.woodsales.data.model.ConfirmSendMoneyRequest
import com.dateroem.woodsales.data.network.ApiService
import com.dateroem.woodsales.data.network.JsonMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import com.dateroem.woodsales.ui.common.friendlyError

enum class PayoutStep { SEND, CONFIRM, SENDING, DONE, FAILED, TIMED_OUT, MANUAL }

data class PayoutState(
    val step: PayoutStep = PayoutStep.SEND,
    val amount: String = "",
    val phoneNumber: String = "",
    val recipientName: String? = null,
    // True once the amount and phone came from the entry itself - the person
    // then only has to press Send; the fields stay tucked behind "Edit".
    val prefilled: Boolean = false,
    val editing: Boolean = false,
    val sandbox: Boolean = false,
    val receipt: String? = null,
    val failureReason: String? = null,
    val waitedSeconds: Int = 0,
    val manualReference: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val transactionId: Int? = null,
    val paidAmount: String = "",
    // Ticked on the confirm screen - sending stays locked until it is.
    val acknowledged: Boolean = false
)

// "when you pay it it asks for a receipt isnt this supposed to fill itself
// since send money is from the company wallet and one can only send money
// the rest is filled and a success message is sent" - the shared behaviour
// of every payout screen (expense Send Money, loan repayment): the recipient
// and amount are looked up from the entry, "Send" fires the payout, and the
// screen then WATCHES it - M-Pesa's own result fills in the receipt, and the
// person gets a success (or failure) message. Nobody types a receipt unless
// M-Pesa payouts aren't switched on at all.
class PayoutController(
    private val api: ApiService,
    private val scope: CoroutineScope,
    private val actorUserId: Int,
    private val recipientTarget: String,
    private val recipientRowId: Int,
    prefilledAmount: Double?,
    private val submit: suspend (amount: Double, phone: String) -> JsonMap
) {
    private val _state = MutableStateFlow(PayoutState(amount = prefilledAmount?.let { formatAmount(it) }.orEmpty()))
    val state: StateFlow<PayoutState> = _state.asStateFlow()

    init {
        scope.launch {
            try {
                val info = api.sendMoneyRecipient(recipientTarget, recipientRowId)
                val s = _state.value
                val phone = (info["recipientPhone"] as? String)?.takeIf { it.isNotBlank() }
                val amount = if (s.amount.isBlank()) (info["amount"] as? Number)?.toDouble()?.let { formatAmount(it) }.orEmpty() else s.amount
                _state.value = s.copy(
                    amount = amount,
                    phoneNumber = phone ?: s.phoneNumber,
                    recipientName = (info["recipientName"] as? String)?.takeIf { it.isNotBlank() },
                    sandbox = info["sandbox"] == true,
                    prefilled = amount.isNotBlank() && phone != null
                )
            } catch (e: Exception) {
                // Nothing on file for this entry - the person fills the fields in.
            }
        }
    }

    // Bumped on every edit so a slow lookup response for an already-
    // superseded number can never land after a newer one and overwrite
    // it with a stale name.
    private var phoneLookupGeneration = 0

    fun updateAmount(v: String) { _state.value = _state.value.copy(amount = v) }

    // "updating the number in the payment in any send money should also
    // show the change [in] the name ... when this payment is paid to a
    // salary to a user whose number is saved in the system the name in
    // the appearing confirmation page should bear that name" - the name
    // used to only ever come from the entry's own original recipient
    // lookup at load time; editing the number never re-resolved it, so
    // the confirmation screen kept showing whoever it started with.
    fun updatePhoneNumber(v: String) {
        _state.value = _state.value.copy(phoneNumber = v, recipientName = null)
        val generation = ++phoneLookupGeneration
        val digits = v.filter { it.isDigit() }
        if (digits.length < 9) return
        scope.launch {
            delay(400) // let typing settle before firing a lookup per keystroke
            if (generation != phoneLookupGeneration) return@launch
            try {
                val result = api.lookupUserByPhone(v.trim())
                if (generation == phoneLookupGeneration) {
                    _state.value = _state.value.copy(recipientName = result["name"] as? String)
                }
            } catch (_: Exception) {
                // No user under that number (a real 404) or unreachable -
                // leave the name blank rather than showing an error for
                // what's usually just an unregistered recipient.
            }
        }
    }

    fun updateManualReference(v: String) { _state.value = _state.value.copy(manualReference = v) }
    fun toggleEditing() { _state.value = _state.value.copy(editing = !_state.value.editing) }

    // Pressing Send only ever leads to the confirmation screen: for security
    // the person must check the details and tick the box before anything is
    // sent (confirmAndSend below is the one that really fires the payout).
    fun send() {
        val s = _state.value
        val amount = s.amount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _state.value = s.copy(error = "Enter a valid amount", editing = true)
            return
        }
        if (s.phoneNumber.isBlank()) {
            _state.value = s.copy(error = "Enter the recipient's phone number", editing = true)
            return
        }
        _state.value = s.copy(step = PayoutStep.CONFIRM, error = null, acknowledged = false, paidAmount = formatAmount(amount))
    }

    fun setAcknowledged(value: Boolean) { _state.value = _state.value.copy(acknowledged = value) }

    fun backToEdit() {
        _state.value = _state.value.copy(step = PayoutStep.SEND, acknowledged = false, editing = true, error = null)
    }

    fun confirmAndSend() {
        val s = _state.value
        if (s.step != PayoutStep.CONFIRM || !s.acknowledged) return
        val amount = s.amount.toDoubleOrNull() ?: return
        _state.value = s.copy(step = PayoutStep.SEND, submitting = true, error = null, acknowledged = false, paidAmount = formatAmount(amount))
        scope.launch {
            try {
                val response = submit(amount, s.phoneNumber.trim())
                val id = (response["financialTransactionId"] as? Number)?.toInt()
                val sandbox = response["sandbox"] == true || _state.value.sandbox
                val immediateFailure = (response["failureReason"] as? String)?.takeIf { it.isNotBlank() }
                val initiated = response["darajaInitiated"] as? Boolean ?: false
                _state.value = _state.value.copy(submitting = false, transactionId = id, sandbox = sandbox)
                when {
                    immediateFailure != null -> _state.value = _state.value.copy(step = PayoutStep.FAILED, failureReason = immediateFailure)
                    !initiated -> _state.value = _state.value.copy(step = PayoutStep.MANUAL)
                    id != null -> watch(id)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describe(e))
            }
        }
    }

    // M-Pesa answers a payout some seconds to a couple of minutes after it is
    // accepted, so this checks every few seconds and gives up waiting (not on
    // the payment) after ten minutes - the sandbox can take five.
    private fun watch(id: Int) {
        _state.value = _state.value.copy(step = PayoutStep.SENDING, waitedSeconds = 0, failureReason = null)
        scope.launch {
            var waited = 0
            while (true) {
                val status = try { api.paymentStatus(id) } catch (e: Exception) { null }
                when (status?.get("status") as? String) {
                    "COMPLETED" -> {
                        _state.value = _state.value.copy(step = PayoutStep.DONE, receipt = status["referenceId"] as? String)
                        return@launch
                    }
                    "FAILED" -> {
                        _state.value = _state.value.copy(
                            step = PayoutStep.FAILED,
                            failureReason = (status["failureReason"] as? String) ?: "M-Pesa didn't complete the payout"
                        )
                        return@launch
                    }
                }
                if (waited >= 600) {
                    _state.value = _state.value.copy(step = PayoutStep.TIMED_OUT)
                    return@launch
                }
                _state.value = _state.value.copy(waitedSeconds = waited)
                delay(3000)
                waited += 3
            }
        }
    }

    fun checkAgain() {
        _state.value.transactionId?.let { watch(it) }
    }

    fun tryAgain() {
        _state.value = _state.value.copy(step = PayoutStep.SEND, error = null, failureReason = null, transactionId = null)
    }

    fun showManual() {
        _state.value = _state.value.copy(step = PayoutStep.MANUAL, error = null)
    }

    // Only reachable when M-Pesa payouts aren't switched on, or after M-Pesa
    // stayed silent: the person confirms by typing the receipt they got.
    fun confirmManual() {
        val s = _state.value
        val id = s.transactionId ?: return
        if (s.manualReference.isBlank()) {
            _state.value = s.copy(error = "Enter the M-Pesa receipt code")
            return
        }
        _state.value = s.copy(submitting = true, error = null)
        scope.launch {
            try {
                api.confirmSendMoney(id, ConfirmSendMoneyRequest(s.manualReference.trim()), actorUserId)
                _state.value = _state.value.copy(submitting = false, step = PayoutStep.DONE, receipt = s.manualReference.trim())
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = describe(e))
            }
        }
    }

    private fun describe(e: Exception): String {
        if (e is HttpException) {
            val body = e.response()?.errorBody()?.string().orEmpty()
            Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.let { return it.groupValues[1] }
            return friendlyError(e, "Something went wrong. Please try again.")
        }
        return friendlyError(e, "Something went wrong")
    }

    private fun formatAmount(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
