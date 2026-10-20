package com.dateroem.woodsales.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.Vehicle
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.common.DateField
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

// Send one customer's group to a light or bulk delivery: pick the trip, then
// an existing delivery on it or a new one, tick the orders that go, confirm.
@Composable
fun AssignGroupScreen(viewModel: AssignGroupViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    val kindWord = if (viewModel.kind == "light") "Light" else "Bulk"
    val group = state.group

    // "allow a user to drop the product if it has not yet been assigned a
    // trip which will allow one to get refunded the money paid" - every
    // order reachable from this screen is still pending by definition.
    var droppingOrder by remember { mutableStateOf<JsonMap?>(null) }
    droppingOrder?.let { order ->
        DropOrderDialog(
            order = order,
            dropping = state.droppingOrderId == order.int("orderId"),
            error = state.dropError,
            onConfirm = { cost -> viewModel.dropOrder(order.int("orderId") ?: return@DropOrderDialog, cost) },
            onDismiss = { droppingOrder = null }
        )
    }
    // Closes the dialog itself once the drop actually succeeds (dropError
    // stays null and droppingOrderId clears back to null on success).
    LaunchedEffect(state.droppingOrderId, state.dropError) {
        if (state.droppingOrderId == null && state.dropError == null && droppingOrder != null) {
            droppingOrder = null
        }
    }
    state.lastDropRefund?.let { refund ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDropRefund,
            title = { Text("Order dropped") },
            text = { Text("KES $refund is owed back to the customer. Pay it out from Transactions → Send Money once you have their number.") },
            confirmButton = { TextButton(onClick = viewModel::dismissDropRefund) { Text("Got it") } }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when {
            state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            group == null -> Column(modifier = Modifier.align(Alignment.Center).padding(24.dp)) {
                Text(state.error ?: "Nothing to assign", color = MaterialTheme.colorScheme.error)
                Button(onClick = onCancel, modifier = Modifier.padding(top = 12.dp)) { Text("Back") }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
            ) {
                Text(
                    "Assign ${group.text("customer") ?: "group"} to a $kindWord delivery",
                    color = DateroemText, fontSize = 19.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    if (state.step == 0) "Step 1 of 2 - choose the trip" else "Step 2 of 2 - Trip #${state.trip?.int("tripId")}: pick or create the delivery, tick the orders",
                    color = DateroemAccent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                if (state.step == 0) {
                    if (state.trips.isEmpty()) {
                        Text(
                            "There's no open trip to put these on. A trip has to be started (and the previous one finalized) first.",
                            color = DateroemTextMuted
                        )
                    }
                    state.trips.forEach { trip ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DateroemSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { viewModel.pickTrip(trip) }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Trip #${trip.int("tripId")}  ·  OPEN", color = DateroemAccent, fontWeight = FontWeight.Bold)
                                Text(trip.text("productDescription") ?: "no description", color = DateroemText)
                                Text("Started ${trip.date("startDate") ?: "?"}", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    if (state.loadingDeliveries) CircularProgressIndicator(color = DateroemAccent)

                    state.deliveries.forEach { choice ->
                        val selected = state.choice == choice
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (selected) DateroemAccent.copy(alpha = 0.22f) else DateroemSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.choose(choice) }
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { viewModel.choose(choice) })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(choice.title, color = DateroemText, fontWeight = FontWeight.SemiBold)
                                    Text(choice.subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (viewModel.kind == "bulk" && state.choice?.isNew == true) {
                        OutlinedTextField(
                            value = state.deliveredTo,
                            onValueChange = viewModel::setDeliveredTo,
                            label = { Text("Delivered to (customer / destination)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                    }
                    // "allow one to name a delivery when assigning a new one
                    // ... a name delivery maasai allows quick identification" -
                    // optional, light-only (bulk already has the field above).
                    if (viewModel.kind == "light" && state.choice?.isNew == true) {
                        OutlinedTextField(
                            value = state.deliveryName,
                            onValueChange = viewModel::setDeliveryName,
                            label = { Text("Name this delivery (optional)") },
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        )
                    }
                    DateField(
                        label = "Delivery date *",
                        value = state.date,
                        onValueChange = viewModel::setDate,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    VehicleDropdown(state.vehicles, state.vehicleId, viewModel::setVehicle)

                    Text("Orders to put on this delivery", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text("Untick any to leave them pending.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                    group.maps("orders").forEach { o ->
                        val id = o.int("lightDeliveryId") ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Checkbox(checked = id in state.ticked, onCheckedChange = { viewModel.toggle(id) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Order #${o.int("orderId")} · ${o.text("itemsText") ?: "no items"}", color = DateroemText, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${o.feet()} body ft · ${if (o.flag("large")) "LARGE" else "small"} · KES ${o.money("total")}",
                                    color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall
                                )
                            }
                            // Still pending by definition on this screen -
                            // safe to offer here without re-checking trip
                            // status client-side (the backend does anyway).
                            TextButton(onClick = { droppingOrder = o }) {
                                Text("Drop", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = if (state.step == 1) viewModel::backToTrips else onCancel, modifier = Modifier.weight(1f)) {
                        Text(if (state.step == 1) "Back" else "Cancel")
                    }
                    if (state.step == 1) {
                        WoodButton(
                            onClick = { viewModel.confirm() },
                            enabled = !state.submitting && !state.loadingDeliveries,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (state.submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                            else Text("Confirm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// "a cost is reduced from the initially sent by the admins who can only
// do this" - the admin types the cancellation cost here; the refund
// itself is computed server-side, never trusted from this screen.
@Composable
private fun DropOrderDialog(order: JsonMap, dropping: Boolean, error: String?, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var costText by remember { mutableStateOf("0") }
    AlertDialog(
        onDismissRequest = { if (!dropping) onDismiss() },
        title = { Text("Drop Order #${order.int("orderId")}?") },
        text = {
            Column {
                Text(
                    "Restores the stock this order took and marks it cancelled. This can't be undone.",
                    color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 10.dp)
                )
                Text("Order total: KES ${order.money("total")}", color = DateroemText)
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text("Cancellation cost (KES)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(costText.toDoubleOrNull() ?: 0.0) },
                enabled = !dropping,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (dropping) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Drop & Refund")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !dropping) { Text("Cancel") } }
    )
}

@Composable
private fun VehicleDropdown(vehicles: List<Vehicle>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = vehicles.firstOrNull { it.vehicleId == selectedId }?.uniqueIdentifier ?: "No vehicle"
    Box(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Vehicle: $label")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("No vehicle") }, onClick = { onSelect(null); open = false })
            vehicles.forEach { v ->
                DropdownMenuItem(
                    text = { Text(v.uniqueIdentifier ?: "Vehicle #${v.vehicleId}") },
                    onClick = { onSelect(v.vehicleId); open = false }
                )
            }
        }
    }
}
