package com.dateroem.woodsales.ui.marketing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.data.model.Campaign
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun DeletedCampaignsScreen(viewModel: DeletedCampaignsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val restoringIds by viewModel.restoringIds.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            is DeletedCampaignsUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is DeletedCampaignsUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load deleted campaigns", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is DeletedCampaignsUiState.Loaded -> {
                if (current.campaigns.isEmpty()) {
                    Text(
                        "Nothing deleted right now.",
                        color = DateroemTextMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn {
                        items(current.campaigns) { campaign ->
                            DeletedCampaignRow(
                                campaign,
                                restoring = campaign.campaignId in restoringIds,
                                onRestore = { viewModel.restore(campaign.campaignId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedCampaignRow(campaign: Campaign, restoring: Boolean, onRestore: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CampaignMediaThumbnail(campaign, modifier = Modifier.size(56.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(campaign.name, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    campaign.channel ?: "",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            WoodButton(
                onClick = onRestore,
                enabled = !restoring,
            ) {
                if (restoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = WoodOn)
                } else {
                    Text("Restore")
                }
            }
        }
    }
}
