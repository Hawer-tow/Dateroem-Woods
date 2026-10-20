package com.dateroem.woodsales.ui.transactions

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton

private val SuccessGreen = Color(0xFF4FA35C)

// "start i will use the sim for number 0700878260 ... implement the
// feature so we can test the system" - one-time SIM pick, then a manual
// resync so today's earlier test messages get caught up too. After this
// runs once, SmsReceiver keeps it current on its own for every new message.
@Composable
fun SmsSyncScreen(viewModel: SmsSyncViewModel) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.refreshPermissionState()
    }

    Column(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        Text("M-Pesa SMS Sync", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Catches payments made through the plain M-Pesa app while this system was offline - it reads only the " +
                "company SIM's M-Pesa messages, never the other line if there is one.",
            color = DateroemTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (!state.hasPermissions) {
            Card(colors = CardDefaults.cardColors(containerColor = DateroemSurface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Permission needed", color = DateroemText, fontWeight = FontWeight.Bold)
                    Text(
                        "To read the company SIM's M-Pesa messages, this needs SMS and phone-state permission.",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    WoodButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.READ_PHONE_STATE)
                            )
                        },
                    ) { Text("Grant permission") }
                }
            }
            return@Column
        }

        Text("Which SIM is the company line?", color = DateroemText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        if (state.availableSims.isEmpty()) {
            Text("No active SIMs found.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(state.availableSims) { sim ->
                    val selected = sim.subscriptionId == state.selectedSubscriptionId
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (selected) SuccessGreen.copy(alpha = 0.16f) else DateroemSurface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { viewModel.selectSim(sim) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(sim.label, color = DateroemText)
                            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = SuccessGreen)
                        }
                    }
                }
            }
        }

        state.selectedLabel?.let {
            Text("Company SIM: $it", color = SuccessGreen, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
        }

        Text(
            "Resync catches messages already sitting in the inbox (the last 30 days) - use it once after picking the " +
                "SIM above, or any time you suspect a message was missed.",
            color = DateroemTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        OutlinedButton(
            onClick = { viewModel.resync() },
            enabled = state.selectedSubscriptionId != null && !state.resyncing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.resyncing) CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            Text(if (state.resyncing) "Scanning inbox..." else "Resync last 30 days")
        }

        state.lastResult?.let { result ->
            Text(
                "Scanned ${result.scanned} M-Pesa message(s) - ${result.submitted} sent, ${result.failed} couldn't be read.",
                color = DateroemTextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (result.unparsedSamples.isNotEmpty()) {
                Text(
                    "A few that couldn't be read (so the format can be added):",
                    color = DateroemText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                result.unparsedSamples.forEach { sample ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Text(
                            sample,
                            color = DateroemTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
