package com.dateroem.woodsales.ui.marketing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemAccentHover
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodFab
import com.dateroem.woodsales.ui.common.ErrorBox

// A retired campaign's card: the same muted wood-brown as a retired product.
private val RetiredSurface = Color(0xFF3C2E1C)

@Composable
fun CampaignsScreen(
    viewModel: CampaignsViewModel,
    onAddCampaign: () -> Unit,
    onEditCampaign: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val busyIds by viewModel.busyIds.collectAsState()
    val actionError by viewModel.actionError.collectAsState()
    var confirming by remember { mutableStateOf<Campaign?>(null) }

    confirming?.let { campaign ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Bring this campaign back?") },
            text = {
                Text(
                    "'${campaign.name}' shows on the website again while it is Active, within its dates " +
                        "and has at least one product."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.bringBack(campaign)
                    confirming = null
                }) { Text("Bring back", color = DateroemAccent) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel", color = DateroemTextMuted) }
            }
        )
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissActionError() },
            title = { Text("Couldn't update this campaign") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.dismissActionError() }) { Text("OK") } }
        )
    }

    Scaffold(
        containerColor = DateroemBackground,
        floatingActionButton = {
            WoodFab(onClick = onAddCampaign) {
                Icon(Icons.Filled.Add, contentDescription = "Add campaign")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val current = state) {
                    is CampaignsUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = DateroemAccent
                        )
                    }
                    is CampaignsUiState.Error -> {
                        Column(modifier = Modifier.align(Alignment.Center)) {
                            Text("Couldn't load campaigns", color = MaterialTheme.colorScheme.error)
                            ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                            Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                                Text("Retry")
                            }
                        }
                    }
                    is CampaignsUiState.Loaded -> {
                        if (current.campaigns.isEmpty()) {
                            Text(
                                "No campaigns yet - tap + to create one.",
                                color = DateroemTextMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn {
                                items(current.campaigns, key = { it.campaignId }) { campaign ->
                                    CampaignRow(
                                        campaign,
                                        busy = campaign.campaignId in busyIds,
                                        onClick = { onEditCampaign(campaign.campaignId) },
                                        onBringBack = { confirming = campaign }
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

// Retiring lives inside the campaign (its edit screen), as for products, so
// it can't be hit by accident from the list; only a retired campaign shows
// a button here, Bring back.
@Composable
private fun CampaignRow(campaign: Campaign, busy: Boolean, onClick: () -> Unit, onBringBack: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (campaign.retired) RetiredSurface else DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // "allow a marketing photo or even the image to be visible" -
            // the photo, or for a video "a minimized view of the video
            // like the first part" (a still from its start).
            CampaignMediaThumbnail(
                campaign,
                modifier = Modifier.size(56.dp).alpha(if (campaign.retired) 0.6f else 1f)
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(campaign.name, color = MaterialTheme.colorScheme.onSurface)
                if (campaign.retired) {
                    // "Retired" chip - off the website, but kept and reversible.
                    Text(
                        "Retired · not on the website",
                        color = DateroemAccentHover,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "${campaign.channel ?: "No channel"} - ${campaign.status ?: "No status"}",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
                if (campaign.startAt != null || campaign.endAt != null) {
                    Text(
                        "${campaign.startAt ?: "?"} to ${campaign.endAt ?: "?"}",
                        color = DateroemAccent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            if (campaign.retired) {
                Spacer(modifier = Modifier.size(8.dp))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = DateroemAccent, strokeWidth = 2.dp)
                } else {
                    // Bringing it back is the positive action: painted wood.
                    WoodButton(onClick = onBringBack) { Text("Bring back", fontSize = 13.sp) }
                }
            }
        }
    }
}
