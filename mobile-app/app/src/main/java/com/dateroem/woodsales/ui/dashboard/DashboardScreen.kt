package com.dateroem.woodsales.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.data.session.CompanyInfo
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel
import com.dateroem.woodsales.ui.orders.DueSoonOrange
import com.dateroem.woodsales.ui.orders.LateRed
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.common.ErrorBox

/**
 * Home. Everyone sees the company card, the picked trip's products and
 * deliveries, and campaigns. Admins and the SuperAdmin also see the business
 * at a glance and the trip's profit and expenses ("Admins and SuperAdmin
 * only", 24 October 2026); only the SuperAdmin edits the company card.
 */
@Composable
fun DashboardScreen(viewModel: DashboardViewModel, isAdmin: Boolean, isSuperAdmin: Boolean) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is DashboardUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is DashboardUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                    Text("Couldn't load the dashboard", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is DashboardUiState.Loaded -> {
                val data = current.data
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 72.dp)) {
                    CompanyCard(data.company, canEdit = isSuperAdmin, onSave = viewModel::saveCompanyInfo)
                    data.overview?.let { Overview(it) }
                    val stats = data.trip
                    if (stats == null) {
                        Text(
                            "No trip is active right now. Tap the wood dial at the bottom to pick one - this page, and every trip section, follows it from there.",
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                        )
                    } else {
                        TripSection(stats, showMoney = isAdmin)
                    }
                    // Admins see campaigns in the overview above.
                    if (data.overview == null) {
                        CampaignsCard(data.campaigns, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    }
                }
            }
        }
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
private fun TripSection(stats: DashboardTripStats, showMoney: Boolean) {
    val trip = stats.trip
    Text(
        (trip["productDescription"] as? String)?.takeIf { it.isNotBlank() } ?: "Active Trip",
        color = DateroemText,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(
        "${trip["startDate"] ?: "?"} - ${trip["endDate"] ?: "ongoing"}",
        color = DateroemTextMuted,
        modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
    )
    // "should Employees and Marketers keep seeing trip profit and expenses" -
    // "Admins and SuperAdmin only" (24 October 2026).
    if (showMoney) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            StatTile(AppIcons.TrendingUp, "Profit", "KES ${stats.profit}", Modifier.weight(1f))
            StatTile(AppIcons.Receipt, "Expenses", "KES ${stats.totalExpenses}", Modifier.weight(1f))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(AppIcons.TreeRings, "Products Sold", "${stats.productsSold}", Modifier.weight(1f))
        StatTile(
            AppIcons.Truck,
            "Deliveries",
            "${stats.bulkDeliveries + stats.lightDeliveries}",
            Modifier.weight(1f),
            subtitle = "${stats.bulkDeliveries} bulk · ${stats.lightDeliveries} light"
        )
    }
}

// --- The business at a glance (Admins and the SuperAdmin) -------------------

@Suppress("UNCHECKED_CAST")
private fun JsonMap.part(key: String): JsonMap = (this[key] as? Map<String, Any?>) ?: emptyMap()

private fun JsonMap.n(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L

private fun plural(n: Long, one: String, many: String) = "$n ${if (n == 1L) one else many}"

@Composable
private fun Overview(overview: JsonMap) {
    val users = overview.part("users")
    val products = overview.part("products")
    val campaigns = overview.part("campaigns")
    val trips = overview.part("trips")
    val orders = overview.part("orders")

    Text("The business at a glance", color = DateroemText, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))

    val waiting = orders.n("waiting")
    val late = orders.n("late")
    val dueSoon = orders.n("dueSoon")
    val reassign = orders.n("reassign")
    GlanceTile(
        AppIcons.Package, "Orders waiting for a delivery", "$waiting",
        lines = listOfNotNull(
            if (late > 0) "$late past the promised date - assign first" to LateRed else null,
            if (dueSoon > 0) "$dueSoon close to the promised date" to DueSoonOrange else null,
            if (reassign > 0) "$reassign missed a delivery" to null else null,
            if (waiting == 0L) "None waiting" to null else null
        ),
        modifier = Modifier.fillMaxWidth()
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        val byRole = users.part("staffByRole").entries.joinToString(" · ") { (role, n) -> "$n $role" }
        GlanceTile(
            AppIcons.Users, "Users", "${users.n("total")}",
            lines = listOfNotNull(
                "${plural(users.n("customers"), "customer", "customers")} · ${users.n("staff")} staff" to null,
                byRole.takeIf { it.isNotBlank() }?.let { it to null },
                users.n("disabled").takeIf { it > 0 }?.let { "$it disabled" to null }
            ),
            modifier = Modifier.weight(1f)
        )
        GlanceTile(
            AppIcons.TreeRings, "Products in the shop", "${products.n("inShop")}",
            lines = listOfNotNull(
                "${products.n("retired")} retired · ${products.n("deleted")} deleted" to null,
                products.n("outOfStock").takeIf { it > 0 }?.let { "$it out of stock" to DueSoonOrange }
            ),
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        GlanceTile(
            AppIcons.Megaphone, "Campaigns", "${campaigns.n("total")}",
            lines = listOf(
                "${campaigns.n("running")} running · ${campaigns.n("upcoming")} upcoming · ${campaigns.n("ended")} ended" to null,
                "${plural(campaigns.n("photos"), "photo", "photos")} · ${plural(campaigns.n("videos"), "video", "videos")}" to null
            ),
            modifier = Modifier.weight(1f)
        )
        GlanceTile(
            AppIcons.Map, "Trips", "${trips.n("total")}",
            lines = if (trips.n("total") == 0L) listOf("None yet" to null) else listOf(
                "${trips.n("inProgress")} in progress · ${trips.n("finished")} finished" to null,
                plural(trips.n("deliveries"), "delivery made", "deliveries made") to null
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun GlanceTile(icon: ImageVector, label: String, value: String, lines: List<Pair<String, Color?>>, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.heightIn(min = 112.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            IconLabel(icon, label, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, iconSize = 14.dp, iconTint = DateroemAccent)
            Text(value, color = DateroemAccent, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp))
            lines.forEach { (text, color) ->
                Text(
                    text,
                    color = color ?: DateroemTextMuted,
                    fontWeight = if (color != null) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
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

// The company card: the same on every phone (kept on the server); "only
// superadmin edits ... others see the motto".
@Composable
private fun CompanyCard(info: CompanyInfo, canEdit: Boolean, onSave: (CompanyInfo) -> Unit) {
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
            if (canEdit) {
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
                    Text("Every phone shows this card.", color = DateroemTextMuted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
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
