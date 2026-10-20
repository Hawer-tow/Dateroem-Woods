package com.dateroem.woodsales.data.model

// "connect major expense to a user not just who created it ... who its
// meant for" - the minimal id+name shape the "For User" picker works
// with (GET users/picker), not the full admin User record.
data class UserOption(val userId: Int, val name: String?)

// Generic across the 8 mobile "create an expense holder" categories
// (transport, communications, machine-expenses, accomodation,
// other-expenses, permits, salaries, farmer-payments) - fields only one or
// two categories use are simply ignored server-side by the rest.
data class CreateExpenseRequest(
    val amount: Double,
    val description: String?,
    val type: String?,
    val vehicleUniqueId: String?,
    val additionalId: String?,
    val machineId: Int?,
    val serialNo: String?,
    val startDate: String?,
    val endDate: String?,
    val farmerId: Int?,
    val treesId: Int?,
    val forUserId: Int?,
    val method: String?
)

// target is the backend's ExpenseTarget enum name, e.g. "SALARIES".
data class AssignTransactionRequest(val target: String, val targetRowId: Int)

// STK pulls money FROM a customer - business owner correction: only for a
// product payment (website) or a bulk-goods payment (any user can send
// one here). Never pre-targets an expense category - that's
// SendMoneyRequest's job, since expenses are paid OUT to a partner.
data class StkPushRequest(val amount: Double, val phoneNumber: String?)

data class ConfirmStkPushRequest(val referenceId: String)

// "Send Money" (Daraja B2C) pays a specific already-recorded expense
// holder out to a partner - business owner correction: "expenses are
// payments done to the other partners ... rather a send money option".
// target/targetRowId are required, unlike StkPushRequest.
data class SendMoneyRequest(val amount: Double, val phoneNumber: String?, val target: String, val targetRowId: Int)

data class ConfirmSendMoneyRequest(val referenceId: String)

// "an option pay loan ... led to the transactions area and the option
// loan repayment for the selected loan" - creates a fresh repayment for
// this loan and sends it, admin-only.
data class PayLoanRequest(val amount: Double, val phoneNumber: String?)

// One M-Pesa SMS off the company-SIM phone - see SmsSyncReceiver/Worker.
// mpesaReferenceId is the receipt code the message itself carries (e.g.
// "UIJ030ETLH"); rawSmsText is kept so an admin assigning the resulting
// transaction later can check it against what M-Pesa actually said.
// "a cost is reduced from the initially sent by the admins" - typed in
// on the drop screen; the backend computes and returns the actual refund.
data class DropOrderRequest(val cancellationCost: Double)

data class IngestSmsRequest(
    val mpesaReferenceId: String,
    val amount: Double?,
    val rawSmsText: String?,
    val transactionType: String?,
    val mpesaBalance: Double?,
    val simSlot: Int?,
    val receivedAt: String?
)
