package com.dateroem.woodsales.ui.bulkorders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.width
import com.dateroem.woodsales.ui.common.AppIcons

@Composable
fun BulkOrderDetailScreen(
    viewModel: BulkOrderDetailViewModel,
    onDeleted: () -> Unit,
    onSendStkPush: (amount: Double?) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val form by viewModel.form.collectAsState()
    val finalizing by viewModel.finalizing.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    val deleted by viewModel.deleted.collectAsState()
    val error by viewModel.error.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(deleted) {
        if (deleted) onDeleted()
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this heavy order?") },
            text = { Text("The negotiation didn't proceed - this removes the order and its delivery record.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DateroemAccent,
        unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
    )

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is BulkOrderDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is BulkOrderDetailUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("Couldn't load this order", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is BulkOrderDetailUiState.Loaded -> {
                val order = current.data.order
                val delivered = order["deliveredAt"] != null
                // "provide a place to allow one to edit since one might
                // have missed on some issues" - the amount fields aren't
                // locked forever once the order is marked delivered.
                var editingPayment by remember(order["bulkDeliveryId"]) { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp)
                ) {
                    Text(order["deliveredTo"] as? String ?: "Heavy order", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    LaunchedEffect(finalizing) {
                        if (!finalizing && error == null && editingPayment) editingPayment = false
                    }

                    if (delivered && !editingPayment) {
                        Text("Delivered ${order["deliveredAt"]}", color = DateroemAccent, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            "Hard cash: KES ${order["hardCashAmount"]}   Soft cash: KES ${order["softCashAmount"]}   Total: KES ${order["amountPaid"]}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            if (order["financialTransactionId"] != null) "Payment matched." else "Soft-cash payment not yet matched to a transaction.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = {
                                viewModel.startEditingPayment(order)
                                editingPayment = true
                            },
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        ) { Text("Edit Payment") }

                        Text("Product delivered", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        if (current.data.production.isEmpty()) {
                            Text("No product description recorded.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            current.data.production.forEach { p -> ProductionRow(p) }
                        }
                    } else if (delivered) {
                        // Editing an already-delivered order: just the cash
                        // split, not the whole first-time form again - the
                        // product entries already recorded stay untouched.
                        Text(
                            "Correcting the amount for this order - the product entries below aren't affected.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = form.hardCashAmount,
                                onValueChange = viewModel::updateHardCash,
                                label = { Text("Hard cash") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.softCashAmount,
                                onValueChange = viewModel::updateSoftCash,
                                label = { Text("Soft cash") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { editingPayment = false }, enabled = !finalizing) { Text("Cancel") }
                            WoodButton(
                                onClick = { viewModel.finalize() },
                                enabled = !finalizing,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (finalizing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                                else Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }

                        Text("Product delivered", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                        if (current.data.production.isEmpty()) {
                            Text("No product description recorded.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                        } else {
                            current.data.production.forEach { p -> ProductionRow(p) }
                        }
                    } else {
                        Text(
                            "Not yet delivered - fill this in once the goods reach the company.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = form.hardCashAmount,
                                onValueChange = viewModel::updateHardCash,
                                label = { Text("Hard cash") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.softCashAmount,
                                onValueChange = viewModel::updateSoftCash,
                                label = { Text("Soft cash") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        TextButton(
                            onClick = { onSendStkPush(viewModel.currentSoftCashAmount()) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(AppIcons.PhoneSend, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send STK Push for the soft cash")
                        }

                        OutlinedTextField(
                            value = form.productType,
                            onValueChange = viewModel::updateProductType,
                            label = { Text("Product type") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        OutlinedTextField(
                            value = form.productDescription,
                            onValueChange = viewModel::updateProductDescription,
                            label = { Text("Product description") },
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        OutlinedTextField(
                            value = form.productMeasurement,
                            onValueChange = viewModel::updateProductMeasurement,
                            label = { Text("Measurement (e.g. cubic meters)") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = form.totalBodyFeet,
                                onValueChange = viewModel::updateTotalBodyFeet,
                                label = { Text("Total body feet") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.productTotal,
                                onValueChange = viewModel::updateProductTotal,
                                label = { Text("Product total") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = fieldColors,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (error != null) {
                            Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                        }

                        WoodButton(
                            onClick = { viewModel.finalize() },
                            enabled = !finalizing,
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                        ) {
                            if (finalizing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                            } else {
                                Text("Mark as Delivered", fontWeight = FontWeight.Bold)
                            }
                        }

                        // "the delete button behind the trip selector
                        // button at the bottom of the screen ... too
                        // close to the rest of the fields" - see
                        // ProductFormScreen's copy of this fix: the trip-
                        // selector FAB floats at a fixed bottom-center
                        // position regardless of scroll, so more gap
                        // above separates this from "Mark as Delivered"
                        // and the spacer below clears the FAB's footprint.
                        TextButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !deleting,
                            modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                        ) {
                            if (deleting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                            } else {
                                Text("Delete Order (negotiation failed)", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(modifier = Modifier.height(96.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductionRow(p: JsonMap) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("${p["productType"] ?: "?"} - KES ${p["productTotal"]}", color = DateroemAccent)
            Text(
                "${p["productMeasurement"] ?: "?"} - ${p["totalBodyFeet"] ?: "?"} body ft",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall
            )
            (p["productDescription"] as? String)?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
