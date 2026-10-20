package com.dateroem.woodsales.ui.deliveries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.orders.date
import com.dateroem.woodsales.ui.orders.int
import com.dateroem.woodsales.ui.orders.money
import com.dateroem.woodsales.ui.orders.text
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton

private val AssignedGreen = Color(0xFF2E7D32)

// What a settle dialog is deciding about: one order to mark delivered or not.
private class SettleRequestUi(
    val title: String,
    val items: List<JsonMap>,
    val defaultChecked: Boolean,
    val closeDeliveryId: Int?
)

@Composable
fun LightDeliveriesScreen(viewModel: LightDeliveriesViewModel) {
    val state by viewModel.uiState.collectAsState()
    val updatingIds by viewModel.updatingIds.collectAsState()
    val message by viewModel.message.collectAsState()
    var confirmOrder by remember { mutableStateOf<JsonMap?>(null) }
    var settle by remember { mutableStateOf<SettleRequestUi?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(12.dp)) {
        when (val current = state) {
            is LightDeliveriesUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            is LightDeliveriesUiState.Error -> Column(modifier = Modifier.align(Alignment.Center)) {
                Text("Couldn't load light deliveries", color = MaterialTheme.colorScheme.error)
                Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is LightDeliveriesUiState.Loaded -> {
                val rows = current.deliveries
                // Top-level deliveries; orders assigned into one live inside it
                // (never shown as extra deliveries), side quests inside their
                // bulk delivery.
                val heads = rows.filter { it.int("parentLightDeliveryId") == null && it.int("bulkDeliveryId") == null }
                val bulkGroups = rows.filter { it.int("bulkDeliveryId") != null }.groupBy { it.int("bulkDeliveryId") }

                if (heads.isEmpty() && bulkGroups.isEmpty()) {
                    Text(
                        "No light deliveries for this trip yet - orders are put on a delivery from Insights > Orders.",
                        color = DateroemTextMuted,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        heads.forEach { head ->
                            item(key = "h${head.int("lightDeliveryId")}") {
                                val headId = head.int("lightDeliveryId")
                                val kids = rows.filter { it.int("parentLightDeliveryId") == headId && it.text("status") != "DELIVERED" }
                                val ownOrder = ((head["total"] as? Number)?.toInt() ?: 0) > 0 && head.text("status") != "DELIVERED"
                                val orders = (if (ownOrder) listOf(head) else emptyList()) + kids
                                val name = head.text("deliveryName")
                                DeliveryCard(
                                    title = if (name != null) "Delivery #$headId - $name" else "Delivery #$headId",
                                    subtitle = listOfNotNull(head.text("status"), head.date("estimatedDeliveryDate")).joinToString(" · "),
                                    orders = orders,
                                    updatingIds = updatingIds,
                                    footerLabel = if (head.text("status") == "DELIVERED") null else "Complete delivery",
                                    onMarkDelivered = { confirmOrder = it },
                                    onFooter = {
                                        settle = SettleRequestUi("Complete Delivery #$headId", orders, defaultChecked = false, closeDeliveryId = headId)
                                    }
                                )
                            }
                        }
                        bulkGroups.forEach { (bulkId, all) ->
                            item(key = "b$bulkId") {
                                val pending = all.filter { it.text("status") != "DELIVERED" }
                                DeliveryCard(
                                    title = "Bulk delivery #$bulkId - side quests",
                                    subtitle = "${pending.size} still to deliver",
                                    orders = pending,
                                    updatingIds = updatingIds,
                                    footerLabel = if (pending.isEmpty()) null else "Side quest results",
                                    onMarkDelivered = { confirmOrder = it },
                                    onFooter = {
                                        settle = SettleRequestUi("Side Quest Results - Bulk Delivery #$bulkId", pending, defaultChecked = true, closeDeliveryId = null)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // "confirm that this has been delivered since one can click from a
    // mistake" - now also captures who received it and their signature,
    // "add a signature area which is stored in the signature blob for
    // the said products for the person to be added to the database".
    confirmOrder?.let { order ->
        val signatureCapture = rememberSignatureCapture()
        // "have the received by by default write the name of the one who
        // ordered ... not unless the circumstances change" - pre-filled
        // from the order's own customer, but still a plain editable field
        // for the (rarer) case someone else actually took delivery.
        var receivedBy by remember(order) { mutableStateOf(order.text("customerName") ?: "") }
        AlertDialog(
            onDismissRequest = { confirmOrder = null },
            title = { Text("Mark as delivered?") },
            text = {
                Column {
                    Text(
                        "Mark order #${order.int("orderId")} as delivered? It is removed from this list and the customer will see it as delivered.",
                        color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = receivedBy,
                        onValueChange = { receivedBy = it },
                        label = { Text("Received by (name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                    SignaturePad(signatureCapture)
                }
            },
            confirmButton = {
                WoodButton(
                    onClick = {
                        val id = order.int("lightDeliveryId")
                        confirmOrder = null
                        if (id != null) {
                            viewModel.markDelivered(id, signatureCapture.toDataUrl(), receivedBy.trim().ifBlank { null })
                        }
                    },
                ) { Text("Yes, delivered") }
            },
            dismissButton = { TextButton(onClick = { confirmOrder = null }) { Text("Cancel") } }
        )
    }

    settle?.let { req ->
        SettleDialog(
            request = req,
            onDismiss = { settle = null },
            onConfirm = { delivered, undelivered, reason ->
                settle = null
                viewModel.settle(delivered, undelivered, reason, req.closeDeliveryId)
            }
        )
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") } },
            text = { Text(it) }
        )
    }
}

@Composable
private fun DeliveryCard(
    title: String,
    subtitle: String,
    orders: List<JsonMap>,
    updatingIds: Set<Int>,
    footerLabel: String?,
    onMarkDelivered: (JsonMap) -> Unit,
    onFooter: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (subtitle.isNotBlank()) Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)

            if (orders.isEmpty()) {
                Text("No pending orders on this delivery.", color = DateroemTextMuted, modifier = Modifier.padding(top = 10.dp))
            }
            orders.forEach { order ->
                val id = order.int("lightDeliveryId")
                val working = id != null && id in updatingIds
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(AssignedGreen.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Order #${order.int("orderId")}", color = DateroemText, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                order.text("areaName"), order.text("deliveryCity"), "KES ${order.money("total")}"
                            ).joinToString(" · "),
                            color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    OutlinedButton(onClick = { onMarkDelivered(order) }, enabled = !working) {
                        if (working) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DateroemAccent)
                        else Text("Delivered", fontSize = 13.sp)
                    }
                }
            }

            if (footerLabel != null) {
                WoodButton(
                    onClick = onFooter,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) { Text(footerLabel, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// Tick what arrived. Everything ticked = everything arrived - no note needed;
// the reason box only appears once at least one order is left unticked.
@Composable
private fun SettleDialog(
    request: SettleRequestUi,
    onDismiss: () -> Unit,
    onConfirm: (delivered: List<Int>, undelivered: List<Int>, reason: String?) -> Unit
) {
    val ticked = remember {
        mutableStateListOf<Int>().apply {
            if (request.defaultChecked) request.items.forEach { o -> o.int("lightDeliveryId")?.let { add(it) } }
        }
    }
    var reason by remember { mutableStateOf("") }
    val allIds = request.items.mapNotNull { it.int("lightDeliveryId") }
    val undelivered = allIds.filter { it !in ticked }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column {
                Text(
                    when {
                        request.items.isEmpty() -> "No pending orders are left on this delivery - it will just be marked delivered."
                        request.defaultChecked -> "Tick each order that was delivered. If all are ticked, no note is needed. Unticked orders go back to the pending list, highlighted as priority."
                        else -> "These orders were never marked delivered, so they go back to the pending list, highlighted as priority. Tick any that did arrive."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                request.items.forEach { o ->
                    val id = o.int("lightDeliveryId")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = id != null && id in ticked,
                            onCheckedChange = { on -> if (id != null) { if (on) ticked.add(id) else ticked.remove(id) } }
                        )
                        Text("Order #${o.int("orderId")} · KES ${o.money("total")}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (undelivered.isNotEmpty()) {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Why weren't some delivered?") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            WoodButton(
                enabled = undelivered.isEmpty() || reason.isNotBlank(),
                onClick = { onConfirm(ticked.toList(), undelivered, reason.takeIf { it.isNotBlank() }) },
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
