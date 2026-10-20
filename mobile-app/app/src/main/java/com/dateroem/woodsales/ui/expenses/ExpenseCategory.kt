package com.dateroem.woodsales.ui.expenses

import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons

// key matches the backend's ExpenseTarget.categoryKey (the {category} path
// segment); totalsKey matches the camelCase key that key comes back as in
// GET trips/{tripId}/expenses' totals map. creatable marks the 8
// categories a normal user can add/delete a holder in from this screen -
// farmer payments are "also part of the expenses ... a very key expense"
// (business owner correction), so they're creatable here too, alongside
// FarmerPaymentController's separate dedicated "send to a farmer" flow.
// LOAN/CAPITAL live entirely on the admin-only Loans & Finance page - not
// yet a mobile screen, view-only here.
enum class ExpenseCategory(
    val key: String,
    val totalsKey: String,
    val label: String,
    val icon: ImageVector,
    val creatable: Boolean,
    // camelCase of the backend's idColumn (JsonKeys.toCamelCase), so a row
    // can be identified generically without a typed model per category.
    val idKey: String,
    // "in most expenses there is that option type ... make it like a
    // selection part rather than typing" - these are exactly the
    // categories with a Type column, which becomes a Soft/Hard cash
    // selector (backend's ExpenseService.HAS_PAYMENT_MODE mirrors this).
    val hasPaymentMode: Boolean = false
) {
    TRANSPORT("transport", "transport", "Transport", AppIcons.Truck, true, "transportId", true),
    COMMUNICATIONS("communications", "communications", "Communications", AppIcons.Smartphone, true, "communicationsId", true),
    MACHINE_EXPENSES("machine-expenses", "machineExpenses", "Machine Expenses", AppIcons.Settings, true, "machineExpensesId", true),
    ACCOMODATION("accomodation", "accomodation", "Accommodation", AppIcons.Bed, true, "accomodationId", true),
    OTHER_EXPENSES("other-expenses", "otherExpenses", "Other Expenses", AppIcons.Package, true, "otherExpensesId"),
    PERMITS("permits", "permits", "Permits", AppIcons.Document, true, "permitId"),
    SALARIES("salaries", "salaries", "Salaries", AppIcons.Wallet, true, "salaryId", true),
    FARMER_PAYMENTS("farmer-payments", "farmerPayments", "Farmer Payments", AppIcons.Sprout, true, "farmerPaymentsId"),
    LOAN("loan", "loan", "Loans", AppIcons.Bank, false, "loanId"),
    CAPITAL("capital", "capital", "Capital", AppIcons.Banknote, false, "capitalId"),
    // Not a real "expense" category (no trip, not creatable/deletable here) -
    // dropOrder() auto-creates the holder; this only exists so the shared
    // send_money/{category}/{rowId}/{amount} route can resolve its key.
    ORDER_REFUND("order-refund", "orderRefund", "Order Refund", AppIcons.Undo, false, "orderId");

    companion object {
        fun fromKey(key: String): ExpenseCategory = entries.first { it.key == key }
    }
}
