package com.dateroem.woodsales.ui.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import androidx.compose.ui.graphics.vector.ImageVector
import com.dateroem.woodsales.ui.common.AppIcons
import com.dateroem.woodsales.ui.common.IconLabel
import com.dateroem.woodsales.ui.common.ErrorBox

// "when one clicks the loans you go in and there you can create a new
// loan ... when you click capital what we have in the trip area should
// have been here ... below the 2 sections in loans and finances just
// below them is the total of the loans and the finances" - this screen
// is now just two tiles (Loans / Capital) that hand off to their own
// screens, with the two totals underneath.
@Composable
fun FinanceOverviewScreen(
    viewModel: FinanceOverviewViewModel,
    onOpenLoans: () -> Unit,
    onOpenCapital: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        when (val current = state) {
            is FinanceOverviewUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is FinanceOverviewUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load loans & finance", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is FinanceOverviewUiState.Loaded -> {
                val totalLoans = current.loans.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
                val totalCapital = current.capital.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }

                Column(modifier = Modifier.fillMaxSize()) {
                    OverviewTile(
                        icon = AppIcons.Bank,
                        title = "Loans",
                        subtitle = "${current.loans.size} loan${if (current.loans.size == 1) "" else "s"} - tap to view or add one",
                        onClick = onOpenLoans
                    )
                    OverviewTile(
                        icon = AppIcons.Banknote,
                        title = "Capital",
                        subtitle = "${current.capital.size} entr${if (current.capital.size == 1) "y" else "ies"} - tap for the cash split & details",
                        onClick = onOpenCapital
                    )

                    Text(
                        "Total Loans: KES $totalLoans",
                        color = DateroemText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        "Total Capital: KES $totalCapital",
                        color = DateroemText,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewTile(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            IconLabel(icon, title, color = DateroemText, fontSize = 16.sp, fontWeight = FontWeight.Bold, iconSize = 18.dp, iconTint = DateroemAccent)
            Text(subtitle, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
