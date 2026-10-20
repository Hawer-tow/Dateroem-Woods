package com.dateroem.woodsales.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab

@Composable
fun ExpenseListScreen(
    viewModel: ExpenseListViewModel,
    category: ExpenseCategory,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onPay: (rowId: Int, amount: Double) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val deletingIds by viewModel.deletingIds.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    val assignPicker by viewModel.assignPicker.collectAsState()

    assignPicker?.let { picker ->
        AlertDialog(
            onDismissRequest = { if (!picker.busy) viewModel.closeAssign() },
            title = { Text("Assign a payment - KES ${picker.amount}") },
            text = {
                Column {
                    Text(
                        "Pick the payment that settled this entry (closest amount first).",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    picker.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    when {
                        picker.loading || picker.busy -> CircularProgressIndicator(color = DateroemAccent)
                        picker.candidates.isEmpty() -> Text("No unassigned payments right now.", color = DateroemTextMuted)
                        else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(picker.candidates) { txn ->
                                val id = (txn["financialTransactionId"] as? Number)?.toInt()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = id != null) { viewModel.assign(id!!) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text("KES ${txn["amount"]}", color = DateroemAccent)
                                    Text(
                                        "${txn["createdAt"] ?: ""}  ${txn["paymentMode"] ?: ""}  ${txn["referenceId"] ?: ""}".trim(),
                                        color = DateroemTextMuted,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.closeAssign() }, enabled = !picker.busy) { Text("Cancel") }
            }
        )
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete this entry?") },
            text = { Text("It was recorded by mistake and hasn't been matched to a real payment yet.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(pendingDeleteId!!)
                    pendingDeleteId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            if (category.creatable) {
                WoodFab(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add ${category.label}")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                ExpenseFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DateroemAccent,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (deleteError != null) {
                Text(deleteError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is ExpenseListUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
                    }
                    is ExpenseListUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text("Couldn't load entries", color = MaterialTheme.colorScheme.error)
                            Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                            Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                    is ExpenseListUiState.Loaded -> {
                        if (current.items.isEmpty()) {
                            Text(
                                "Nothing here yet" + if (category.creatable) " - tap + to record one." else ".",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn {
                                items(current.items) { row ->
                                    val rowId = (row[category.idKey] as? Number)?.toInt()
                                    val amount = (row["amount"] as? Number)?.toDouble()
                                    val pending = row["financialTransactionId"] == null
                                    ExpenseRow(
                                        row = row,
                                        category = category,
                                        deleting = rowId != null && rowId in deletingIds,
                                        onDelete = if (category.creatable && rowId != null && pending) {
                                            { pendingDeleteId = rowId }
                                        } else null,
                                        onAssign = if (isAdmin && rowId != null && pending && category != ExpenseCategory.LOAN && category != ExpenseCategory.CAPITAL) {
                                            { viewModel.openAssign(rowId, amount ?: 0.0) }
                                        } else null,
                                        onPay = if (isAdmin && rowId != null && pending && category != ExpenseCategory.LOAN && category != ExpenseCategory.CAPITAL) {
                                            { onPay(rowId, amount ?: 0.0) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(
    row: JsonMap,
    category: ExpenseCategory,
    deleting: Boolean,
    onDelete: (() -> Unit)?,
    onAssign: (() -> Unit)?,
    onPay: (() -> Unit)?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val amount = row["amount"] ?: row["amountPaid"]
                Text("KES $amount", color = DateroemAccent)
                (row["description"] as? String)?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                }
                val assigned = row["financialTransactionId"] != null
                Text(
                    if (assigned) "Paid - assigned to payment #${row["financialTransactionId"]}" else "Not paid yet - tap Assign or Pay",
                    color = if (assigned) DateroemAccent else DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (onAssign != null) {
                TextButton(onClick = onAssign) { Text("Assign") }
            }
            if (onPay != null) {
                TextButton(onClick = onPay) { Text("Pay") }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, enabled = !deleting) {
                    if (deleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.error, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
