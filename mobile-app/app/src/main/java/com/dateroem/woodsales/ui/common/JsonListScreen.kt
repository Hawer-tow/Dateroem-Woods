package com.dateroem.woodsales.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

@Composable
fun JsonListScreen(
    viewModel: JsonListViewModel,
    emptyLabel: String,
    titleKey: String = "name"
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        when (val current = state) {
            is JsonListUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is JsonListUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load this section", color = MaterialTheme.colorScheme.error)
                    Text(current.message, color = DateroemTextMuted, modifier = Modifier.padding(top = 4.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is JsonListUiState.Loaded -> {
                if (current.rows.isEmpty()) {
                    Text(emptyLabel, color = DateroemTextMuted, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn {
                        items(current.rows) { row ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DateroemSurface),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        row[titleKey]?.toString() ?: "Untitled",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    row.filterKeys { it != titleKey }.forEach { (key, value) ->
                                        Text(
                                            "$key: $value",
                                            color = DateroemTextMuted,
                                            style = MaterialTheme.typography.bodySmall
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
}
