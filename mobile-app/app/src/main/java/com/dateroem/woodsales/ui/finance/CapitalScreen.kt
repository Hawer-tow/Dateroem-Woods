package com.dateroem.woodsales.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel

@Composable
fun CapitalScreen(viewModel: CapitalViewModel) {
    val state by viewModel.uiState.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val recalculating by viewModel.recalculating.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is CapitalUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is CapitalUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("Couldn't load capital", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is CapitalUiState.Loaded -> {
                var hardCashInput by remember(current.trip) { mutableStateOf(current.trip["hardCash"]?.toString().orEmpty()) }
                var softCashInput by remember(current.trip) { mutableStateOf(current.trip["softCash"]?.toString().orEmpty()) }
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DateroemAccent,
                    unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
                )
                val totalCapital = current.capital.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    IconLabel(AppIcons.Banknote, "Capital", color = DateroemText, fontSize = 18.sp, fontWeight = FontWeight.Bold, iconSize = 20.dp, iconTint = DateroemAccent)
                    Text(
                        "Total: KES $totalCapital",
                        color = DateroemAccent,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "Cash split - \"the hardcash part is written manually\"",
                                color = DateroemText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                "Total Payment: KES ${current.trip["totalPayment"]}",
                                color = DateroemTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = hardCashInput,
                                    onValueChange = { hardCashInput = it },
                                    label = { Text("Hard cash") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = softCashInput,
                                    onValueChange = { softCashInput = it },
                                    label = { Text("Soft cash") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    colors = fieldColors,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                WoodButton(
                                    onClick = {
                                        val hard = hardCashInput.toDoubleOrNull() ?: 0.0
                                        val soft = softCashInput.toDoubleOrNull() ?: 0.0
                                        viewModel.saveCashSplit(hard, soft)
                                    },
                                    enabled = !saving,
                                ) {
                                    if (saving) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WoodOn)
                                    } else {
                                        Text("Save Split")
                                    }
                                }
                                Button(
                                    onClick = { viewModel.recalculateTotalPayment() },
                                    enabled = !recalculating,
                                    colors = ButtonDefaults.buttonColors(containerColor = DateroemBackground)
                                ) {
                                    if (recalculating) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DateroemAccent, strokeWidth = 2.dp)
                                    } else {
                                        Text("Recalculate Total", color = DateroemAccent)
                                    }
                                }
                            }
                        }
                    }

                    Text("Recorded Capital", color = DateroemText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    if (current.capital.isEmpty()) {
                        Text("No capital recorded for this trip.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                    } else {
                        current.capital.forEach { entry -> CapitalRow(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapitalRow(entry: JsonMap) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("KES ${entry["amount"]}", color = DateroemAccent)
            (entry["description"] as? String)?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
