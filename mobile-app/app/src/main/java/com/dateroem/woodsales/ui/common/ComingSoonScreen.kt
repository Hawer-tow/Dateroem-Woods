package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

/**
 * The mobileServer endpoints for this section exist and are reachable
 * (needs a trip/actor selection first, e.g. GET trips/{id}/expenses) - the
 * UI to drive that selection is the next layer to build, not a stub of a
 * missing API.
 */
@Composable
fun ComingSoonScreen(title: String, note: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(note, color = DateroemTextMuted, modifier = Modifier.padding(top = 8.dp))
    }
}
