package com.dateroem.woodsales.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.session.CompanyInfo
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, isAdmin: Boolean) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is DashboardUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("Couldn't load the dashboard", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is DashboardUiState.NoTripSelected -> {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp)) {
                    CompanyCard(current.companyInfo, isAdmin, onSave = viewModel::saveCompanyInfo)
                    Text(
                        "No trip is active right now. Tap the wood dial at the bottom to pick one - this page, and every trip section, follows it from there.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                    )
                    CampaignsCard(current.campaigns)
                }
            }
            is DashboardUiState.TripSelected -> {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp)) {
                    val trip = current.stats.trip
                    Text(
                        (trip["productDescription"] as? String)?.takeIf { it.isNotBlank() } ?: "Active Trip",
                        color = DateroemText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${trip["startDate"] ?: "?"} - ${trip["endDate"] ?: "ongoing"}",
                        color = DateroemTextMuted,
                        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatTile(AppIcons.TrendingUp, "Profit", "KES ${current.stats.profit}", Modifier.weight(1f))
                        StatTile(AppIcons.Receipt, "Expenses", "KES ${current.stats.totalExpenses}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        StatTile(AppIcons.TreeRings, "Products Sold", "${current.stats.productsSold}", Modifier.weight(1f))
                        StatTile(
                            AppIcons.Truck,
                            "Deliveries",
                            "${current.stats.bulkDeliveries + current.stats.lightDeliveries}",
                            Modifier.weight(1f),
                            subtitle = "${current.stats.bulkDeliveries} bulk · ${current.stats.lightDeliveries} light"
                        )
                    }
                    CampaignsCard(current.campaigns, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(96.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IconLabel(icon, label, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, iconSize = 14.dp, iconTint = DateroemAccent)
            Text(value, color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 4.dp))
            if (subtitle != null) {
                Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun CampaignsCard(campaigns: DashboardCampaignStats, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IconLabel(AppIcons.Megaphone, "Campaigns", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, iconSize = 14.dp, iconTint = DateroemAccent)
            Text(
                "${campaigns.completed} done of ${campaigns.total}",
                color = DateroemAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CompanyCard(info: CompanyInfo, isAdmin: Boolean, onSave: (CompanyInfo) -> Unit) {
    var showEdit by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(info.name, color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(info.motto, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Code: ${info.code}",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (isAdmin) {
                TextButton(onClick = { showEdit = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Edit", color = DateroemAccent)
                }
            }
        }
    }

    if (showEdit) {
        var name by remember { mutableStateOf(info.name) }
        var motto by remember { mutableStateOf(info.motto) }
        var code by remember { mutableStateOf(info.code) }
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = DateroemAccent,
            unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
        )
        AlertDialog(
            onDismissRequest = { showEdit = false },
            title = { Text("Edit Company Info") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("Name") },
                        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = motto, onValueChange = { motto = it }, label = { Text("Motto") },
                        colors = fieldColors, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it }, label = { Text("Company code") },
                        singleLine = true, colors = fieldColors, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(CompanyInfo(name.trim(), motto.trim(), code.trim()))
                    showEdit = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEdit = false }) { Text("Cancel") }
            }
        )
    }
}
