package com.dateroem.woodsales.ui.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.orders.int
import com.dateroem.woodsales.ui.orders.maps
import com.dateroem.woodsales.ui.orders.text
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel

// "i should not have a financial filling area what i should see are the
// major details pertaining the trip e.g heavy delivery number permits
// all this but for the loan and financing it should be a total of the 2
// ... if you click it you go to the loans and financial section for more
// details" - the cash-split editing form that used to live here moved to
// the Capital screen; this is just the trip's own operational details
// plus one combined finance total.
@Composable
fun TripDetailScreen(
    viewModel: TripDetailViewModel,
    onOpenHeavyDeliveries: () -> Unit,
    onOpenLightDeliveries: () -> Unit,
    onOpenPermits: () -> Unit,
    onOpenFinance: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val finalization by viewModel.finalization.collectAsState()
    val finalizing by viewModel.finalizing.collectAsState()
    val finalizeError by viewModel.finalizeError.collectAsState()

    finalization?.let { check ->
        FinalizeTripDialog(
            check = check,
            working = finalizing,
            error = finalizeError,
            onDismiss = { viewModel.dismissFinalize() },
            onConfirm = { drop -> viewModel.finalizeTrip(drop) }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is TripDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is TripDetailUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("Couldn't load this trip", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is TripDetailUiState.Loaded -> {
                val data = current.data

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    (data.trip["productDescription"] as? String)?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = DateroemText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "${data.trip["startDate"] ?: "?"} - ${data.trip["endDate"] ?: "ongoing"}",
                        color = DateroemTextMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                    )

                    SectionCard(title = "Trip Summary") {
                        SummaryLine("Total Payment", data.summary["totalPayment"])
                        SummaryLine("Profit", data.summary["profit"])
                        SummaryLine("Total Body Feet", data.trip["totalBodyFeet"])
                    }

                    NavRow(AppIcons.Truck, "Heavy Deliveries", "${data.bulkCount} recorded", onOpenHeavyDeliveries)
                    NavRow(AppIcons.Package, "Light Deliveries", "${data.lightCount} recorded", onOpenLightDeliveries)
                    NavRow(AppIcons.Document, "Permits", "View permits for this trip", onOpenPermits)
                    NavRow(AppIcons.Bank, "Loans & Finance", "Total: KES ${data.totalFinance}", onOpenFinance)

                    // "you can never create a new trip before you finalize
                    // every detail in the other trip" - the one way to close it.
                    val endDate = data.trip["endDate"] as? String
                    if (endDate == null) {
                        WoodButton(
                            onClick = { viewModel.openFinalize() },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) { Text("Finalize Trip", fontWeight = FontWeight.Bold) }
                    } else {
                        Text("Finalized ${endDate.take(10)}", color = DateroemTextMuted, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: Any?) {
    Text("$label: KES $value", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IconLabel(icon, title, color = DateroemAccent, fontWeight = FontWeight.Bold, iconSize = 18.dp)
            Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun FinalizeTripDialog(
    check: com.dateroem.woodsales.data.network.JsonMap,
    working: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (dropUndelivered: Boolean) -> Unit
) {
    val pending = check.maps("pendingOrders")
    val bulk = check.maps("pendingBulkDeliveries")
    val loans = check.maps("activeLoans")
    val open = check["open"] == true
    var drop by remember { mutableStateOf(false) }
    val canFinalize = check["canFinalize"] == true && (pending.isEmpty() || drop)

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Finalize trip") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (!open) {
                    Text("This trip is already finalized.")
                } else {
                    Text(
                        if (pending.isEmpty()) "Delivered: every order on this trip is delivered."
                        else "Delivered: ${pending.size} order(s) are NOT delivered yet.",
                        fontWeight = FontWeight.SemiBold
                    )
                    pending.take(6).forEach { Text("  • Order #${it.int("orderId")}  KES ${it["total"]}", style = MaterialTheme.typography.bodySmall) }
                    if (pending.size > 6) Text("  … and ${pending.size - 6} more", style = MaterialTheme.typography.bodySmall)

                    Text(
                        if (bulk.isEmpty()) "Bulk deliveries: all finished." else "Bulk deliveries: ${bulk.size} not finished - finalize or delete them first.",
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
                    )
                    bulk.forEach { Text("  • Bulk delivery #${it.int("bulkDeliveryId")}  ${it.text("deliveredTo") ?: ""}", style = MaterialTheme.typography.bodySmall) }

                    Text(
                        if (loans.isEmpty()) "Loans: all settled." else "Loans: ${loans.size} still ACTIVE - mark them Paid or Written off first.",
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)
                    )
                    loans.forEach { Text("  • Loan #${it.int("loanId")}  KES ${it["amount"]}  ${it.text("description") ?: ""}", style = MaterialTheme.typography.bodySmall) }

                    Text(
                        "Profit: the trip's total payment and profit are recalculated and recorded when you finalize.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp)
                    )
                    if (pending.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                            Checkbox(checked = drop, onCheckedChange = { drop = it })
                            Text(
                                "Drop the ${pending.size} undelivered order(s): they go back to the pending list, highlighted as priority for the next trip.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp)) }
            }
        },
        confirmButton = {
            WoodButton(
                enabled = open && canFinalize && !working,
                onClick = { onConfirm(drop) },
            ) {
                if (working) CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp, color = WoodOn)
                else Text("Finalize Trip")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = onDismiss) { Text("Close") } }
    )
}
