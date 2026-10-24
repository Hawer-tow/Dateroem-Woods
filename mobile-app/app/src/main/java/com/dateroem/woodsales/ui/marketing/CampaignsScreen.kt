package com.dateroem.woodsales.ui.marketing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodFab
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun CampaignsScreen(
    viewModel: CampaignsViewModel,
    onAddCampaign: () -> Unit,
    onEditCampaign: (Int) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
                                items(current.campaigns) { campaign ->
                                    CampaignRow(campaign, onClick = { onEditCampaign(campaign.campaignId) })
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
private fun CampaignRow(campaign: Campaign, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // "allow a marketing photo or even the image to be visible" -
            // the photo, or for a video "a minimized view of the video
            // like the first part" (a still from its start).
            CampaignMediaThumbnail(campaign, modifier = Modifier.size(56.dp))
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(campaign.name, color = MaterialTheme.colorScheme.onSurface)
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
        }
    }
}
