package com.dateroem.woodsales.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.dateroem.woodsales.ui.common.ErrorBox

@Composable
fun ExpenseCategoriesScreen(viewModel: ExpenseCategoriesViewModel, onSelectCategory: (ExpenseCategory) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground).padding(16.dp)) {
        when (val current = state) {
            is ExpenseCategoriesUiState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            }
            is ExpenseCategoriesUiState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    Text("Couldn't load totals", color = MaterialTheme.colorScheme.error)
                    ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                    Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            is ExpenseCategoriesUiState.Loaded -> {
                // "lets remove the financial and loans there in the
                // expenses lets take them there [to Loans & Finance]" -
                // that section now owns Loan/Capital entirely.
                //
                // ORDER_REFUND is excluded too - it's not a real trip-scoped
                // expense category (ORDERS has no Trip_ID column at all, so
                // this screen's usual lineItems(tripId, category) query 500s
                // on it), it only exists in the ExpenseCategory enum so the
                // shared send_money/{category}/... route can resolve its
                // key. Refunds live on their own dedicated Transactions ->
                // Refunds screen instead.
                val categories = ExpenseCategory.entries.filter {
                    it != ExpenseCategory.LOAN && it != ExpenseCategory.CAPITAL && it != ExpenseCategory.ORDER_REFUND
                }
                LazyColumn {
                    items(categories) { category ->
                        val total = (current.totals[category.totalsKey] as? Number)?.toDouble() ?: 0.0
                        CategoryRow(category, total, onClick = { onSelectCategory(category) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: ExpenseCategory, total: Double, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(category.icon, contentDescription = null, tint = DateroemAccent, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(category.label, color = MaterialTheme.colorScheme.onSurface)
                if (!category.creatable) {
                    Text(
                        "View only here",
                        color = DateroemTextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text("KES $total", color = DateroemAccent, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
