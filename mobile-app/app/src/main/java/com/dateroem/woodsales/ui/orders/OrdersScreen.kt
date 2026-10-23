package com.dateroem.woodsales.ui.orders

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemDanger
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton

private val AssignedGreen = Color(0xFF2E7D32)
private val PriorityPurple = Color(0xFF8E44AD)
private val LightBlue = Color(0xFF3B82C4)

// Insights -> Orders, on the phone: one card per customer (per delivery
// area) rather than a table row - the goods gauge up top, the customer's
// already-assigned batch first, their new orders underneath, and the actions
// as buttons at the bottom of the card.
@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel,
    onAssign: (customerId: Int, areaId: Int, kind: String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val working by viewModel.working.collectAsState()
    val message by viewModel.message.collectAsState()
    var batchDialogFor by remember { mutableStateOf<JsonMap?>(null) }

    // "allow the orders area to reload again since if i droped i came back
    // to orders but it had not read the change" - this screen's ViewModel
    // survives while its NavBackStackEntry stays on the back stack (e.g.
    // pushing a drop/assign screen on top and popping back), so refresh()
    // from init{} never reran on its own; re-running it on every ON_RESUME
    // picks up whatever changed while the user was on that other screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is OrdersUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            is OrdersUiState.Error -> Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                Text("Couldn't load the orders", color = MaterialTheme.colorScheme.error)
                Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is OrdersUiState.Loaded -> {
                if (current.groups.isEmpty()) {
                    Column(modifier = Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No pending orders - everything has been assigned to a delivery.",
                            color = DateroemTextMuted
                        )
                        OutlinedButton(onClick = { viewModel.refresh() }, enabled = !working, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Reload orders", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(
                                "Pending orders gathered per customer and delivery area, oldest first. Gauge the goods, then send a group to a light or bulk delivery.",
                                color = DateroemTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        // "add a reload feature on top there at the orders ...
                        // to allow one to manually do it if one is still in
                        // that screen" - the ON_RESUME refresh above covers
                        // coming back from another screen; this covers
                        // staying put while someone else changes something.
                        item {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                                OutlinedButton(onClick = { viewModel.refresh() }, enabled = !working) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Reload orders", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                        itemsIndexed(current.groups) { index, group ->
                            GroupCard(
                                number = index + 1,
                                group = group,
                                onAddToBatch = { batchDialogFor = group },
                                onAssign = { kind -> onAssign(group.int("customerId") ?: -1, group.int("areaId") ?: -1, kind) }
                            )
                        }
                        item { Box(modifier = Modifier.padding(bottom = 16.dp)) }
                    }
                }
            }
        }

        if (working) CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(28.dp), color = DateroemAccent)
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearMessage() },
            confirmButton = { TextButton(onClick = { viewModel.clearMessage() }) { Text("OK") } },
            text = { Text(it) }
        )
    }

    batchDialogFor?.let { group ->
        AddToBatchDialog(
            group = group,
            onDismiss = { batchDialogFor = null },
            onConfirm = { batch, ids ->
                batchDialogFor = null
                viewModel.addToBatch(batch, ids)
            }
        )
    }
}

@Composable
private fun GroupCard(number: Int, group: JsonMap, onAddToBatch: () -> Unit, onAssign: (String) -> Unit) {
    val kind = group.text("kind") ?: "LIGHT"
    val batches = group.maps("assignedBatches")
    val orders = group.maps("orders")
    val priority = group.flag("needsReassignment")

    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(end = 10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.text("customer") ?: "(unknown customer)", color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(group.text("area") ?: "no delivery area", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                }
                KindBadge(kind)
            }

            // A scrollable row of pills - FlowRow isn't in the library version
            // the phone ships with (NoSuchMethodError when the card opened).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState())
            ) {
                Pill("${group.int("orderCount") ?: orders.size} new order(s)", DateroemAccent)
                if ((group.int("assignedCount") ?: 0) > 0) Pill("${group.int("assignedCount")} already assigned", AssignedGreen)
                Pill("KES ${group.money("total")}", DateroemTextMuted)
                Pill("${group.feet()} body ft", DateroemTextMuted)
                if (priority) Pill("REASSIGN FIRST", PriorityPurple)
                val risk = group.text("deadlineRisk")
                val color = deadlineColor(risk)
                val text = deadlineText(risk, group.int("soonestDaysLeft"))
                if (color != null && text != null) Pill(text, color)
            }

            Text(gaugeText(group, kind, orders.size), color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))

            // The customer's assigned batch first ...
            batches.forEach { batch ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp).background(AssignedGreen.copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Text("ALREADY ASSIGNED", color = AssignedGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(batchLabel(batch), color = DateroemText, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    batch.maps("orders").forEach { o ->
                        Text(
                            "Order ${o.ticket()} · ${o.text("itemsText") ?: "no items"} · KES ${o.money("total")}",
                            color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }
            }

            // ... then the new, still-unassigned orders underneath it.
            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(
                    if (batches.isEmpty()) "ORDERS TO ASSIGN" else "NEW - CAN JOIN THE BATCH ABOVE",
                    color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp
                )
                orders.forEach { o ->
                    val urgent = o.flag("needsReassignment")
                    val deadline = deadlineColor(o.text("deadlineRisk"))
                    val tint = deadline ?: if (urgent) PriorityPurple else null
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            .background(tint?.copy(alpha = 0.22f) ?: Color.Transparent, RoundedCornerShape(8.dp))
                            .padding(if (tint != null) 8.dp else 0.dp)
                    ) {
                        Text(
                            "Order ${o.ticket()} · ${o.text("itemsText") ?: "no items"}",
                            color = DateroemText, style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "${o.feet()} body ft · ${if (o.flag("large")) "LARGE" else "small"} · KES ${o.money("total")} · ordered ${o.date("orderedAt") ?: "-"}",
                            color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall
                        )
                        if (urgent) Text("Missed an earlier delivery: ${o.text("undeliveredReason") ?: ""}", color = PriorityPurple, style = MaterialTheme.typography.bodySmall)
                        deadlineText(o.text("deadlineRisk"), o.int("daysLeft"))?.let { text ->
                            Text("$text (promised ${o.date("promisedOn") ?: "-"})", color = deadline ?: DateroemTextMuted,
                                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (batches.isNotEmpty()) {
                    Button(
                        onClick = onAddToBatch,
                        colors = ButtonDefaults.buttonColors(containerColor = AssignedGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add to existing batch", fontWeight = FontWeight.Bold) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val lightRecommended = kind == "LIGHT" || kind == "COMBINED"
                    val bulkRecommended = kind == "BULK" || kind == "COMBINED"
                    DeliveryButton("Light delivery", lightRecommended, Modifier.weight(1f)) { onAssign("light") }
                    DeliveryButton("Bulk delivery", bulkRecommended, Modifier.weight(1f)) { onAssign("bulk") }
                }
            }
        }
    }
}

@Composable
private fun DeliveryButton(label: String, recommended: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (recommended) {
        WoodButton(onClick = onClick, modifier = modifier) {
            Text("Assign to $label", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text("Assign to $label", fontSize = 13.sp) }
    }
}

private fun gaugeText(group: JsonMap, kind: String, orderCount: Int): String {
    val large = group.int("largeOrders") ?: 0
    val base = when (kind) {
        "BULK" -> "Every order holds a large item (over 500 body ft per unit) - suits a bulk delivery."
        "COMBINED" -> "Mixed: $large of $orderCount orders hold large items. The large ones suit a bulk delivery; the small ones can ride along as side quests - or send the whole group light."
        else -> "All small items - suits a light delivery."
    }
    return if (group.flag("unmeasured")) "$base Some products have no size set yet, so they count as small." else base
}

@Composable
private fun KindBadge(kind: String) {
    val color = when (kind) {
        "BULK" -> DateroemAccent
        "COMBINED" -> PriorityPurple
        else -> LightBlue
    }
    Text(
        kind,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun AddToBatchDialog(group: JsonMap, onDismiss: () -> Unit, onConfirm: (batch: JsonMap, lightDeliveryIds: List<Int>) -> Unit) {
    val batches = group.maps("assignedBatches")
    val orders = group.maps("orders")
    var chosen by remember { mutableStateOf(0) }
    val ticked = remember { mutableStateListOf<Int>().apply { orders.forEach { o -> o.int("lightDeliveryId")?.let { add(it) } } } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to existing batch") },
        text = {
            Column {
                Text("Pick the batch this customer already has, and tick the new orders worth adding to it.", style = MaterialTheme.typography.bodySmall)
                batches.forEachIndexed { i, batch ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        RadioButton(selected = chosen == i, onClick = { chosen = i })
                        Text(batchLabel(batch), style = MaterialTheme.typography.bodySmall)
                    }
                }
                orders.forEach { o ->
                    val id = o.int("lightDeliveryId")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = id != null && id in ticked,
                            onCheckedChange = { on -> if (id != null) { if (on) ticked.add(id) else ticked.remove(id) } }
                        )
                        Text("Order ${o.ticket()} · ${o.text("itemsText") ?: ""} · KES ${o.money("total")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            WoodButton(
                enabled = ticked.isNotEmpty() && batches.isNotEmpty(),
                onClick = { onConfirm(batches[chosen], ticked.toList()) },
            ) { Text("Add to batch") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
