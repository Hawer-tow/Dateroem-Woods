package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.common.ErrorBox

// Carried straight from the list row that was tapped (Assign Transactions /
// View All Unassigned) - no second fetch just to show what's already known.
data class TransactionSummary(
    val financialTransactionId: Int,
    val amount: String?,
    val createdAt: String?,
    val referenceId: String?,
    val paymentMode: String?
)

@Composable
fun AssignDetailScreen(viewModel: AssignDetailViewModel, transactionSummary: TransactionSummary? = null, onAssigned: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val assigning by viewModel.assigning.collectAsState()
    val assignError by viewModel.assignError.collectAsState()
    val assigned by viewModel.assigned.collectAsState()
    var pending by remember { mutableStateOf<JsonMap?>(null) }

    LaunchedEffect(assigned) {
        if (assigned) onAssigned()
    }

    if (pending != null) {
        val candidate = pending!!
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Assign this transaction?") },
            text = { Text("It'll be matched to this ${candidate["category"]} entry (KES ${candidate["amount"]}) and can't be undone from here.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = candidate["target"] as? String
                    val rowId = (candidate["rowId"] as? Number)?.toInt()
                    if (target != null && rowId != null) viewModel.assign(target, rowId)
                    pending = null
                }) { Text("Assign") }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        transactionSummary?.let { TransactionSummaryCard(it) }
        Box(modifier = Modifier.weight(1f).fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            is AssignDetailUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is AssignDetailUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load candidates", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is AssignDetailUiState.Loaded -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        "Ranked by matching amount and closest recorded time - tap the right one.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (assignError != null) {
                        ErrorBox(assignError!!, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (current.candidates.isEmpty()) {
                        Text(
                            "No matching un-assigned entries found for this amount yet.",
                            color = DateroemTextMuted,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 40.dp)
                        )
                    } else {
                        LazyColumn {
                            items(current.candidates) { candidate ->
                                CandidateRow(candidate, enabled = !assigning, onClick = { pending = candidate })
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
private fun TransactionSummaryCard(summary: TransactionSummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Transaction #${summary.financialTransactionId}" + (summary.amount?.let { " - KES $it" } ?: ""),
                color = DateroemAccent,
                style = MaterialTheme.typography.titleMedium
            )
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    "Recorded: ${summary.createdAt ?: "?"}",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
                summary.paymentMode?.let {
                    Text("  -  $it", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            summary.referenceId?.let {
                Text("Ref: $it", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: JsonMap, enabled: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${candidate["category"]} - KES ${candidate["amount"]}", color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Recorded ${candidate["createdAt"] ?: "?"}",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                val diff = (candidate["timeDiffSeconds"] as? Number)?.toLong()
                if (diff != null) {
                    Text(
                        "~${formatTimeDiff(diff)} from the payment's recorded time",
                        color = DateroemAccent,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatTimeDiff(seconds: Long): String {
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days > 0 -> "$days day${if (days == 1L) "" else "s"}"
        hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"}"
        minutes > 0 -> "$minutes minute${if (minutes == 1L) "" else "s"}"
        else -> "$seconds seconds"
    }
}
