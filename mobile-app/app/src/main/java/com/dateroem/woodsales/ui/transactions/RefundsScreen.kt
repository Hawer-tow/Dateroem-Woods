package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dateroem.woodsales.data.network.JsonMap
import com.dateroem.woodsales.ui.orders.int
import com.dateroem.woodsales.ui.orders.money
import com.dateroem.woodsales.ui.orders.text
import com.dateroem.woodsales.ui.orders.ticket
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.common.ErrorBox

// "where exactly do i go ... still cannot see it" - the list dropOrder()'s
// refund figure was always missing until now: every dropped order still
// owing its customer money, one Pay button away from Send Money.
@Composable
fun RefundsScreen(viewModel: RefundsViewModel, onPay: (orderId: Int, amount: Double) -> Unit) {
    val state by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(DateroemBackground)) {
        when (val current = state) {
            is RefundsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = DateroemAccent)
            is RefundsUiState.Error -> Column(modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                Text("Couldn't load refunds", color = MaterialTheme.colorScheme.error)
                ErrorBox(current.message, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = { viewModel.refresh() }, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is RefundsUiState.Loaded -> {
                if (current.refunds.isEmpty()) {
                    Text(
                        "No refunds owing right now - every dropped order has been paid back.",
                        color = DateroemTextMuted,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(current.refunds) { refund ->
                            RefundCard(refund, onPay)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefundCard(refund: JsonMap, onPay: (orderId: Int, amount: Double) -> Unit) {
    val orderId = refund.int("orderId")
    val amount = (refund["refundedAmount"] as? Number)?.toDouble()
    Card(
        colors = CardDefaults.cardColors(containerColor = DateroemSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Order ${refund.ticket()}", color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(refund.text("customerName") ?: "(unknown customer)", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                Text("KES ${refund.money("refundedAmount")}", color = DateroemAccent, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                refund.text("cancelledAt")?.let {
                    Text("Dropped $it", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            WoodButton(
                onClick = { if (orderId != null && amount != null) onPay(orderId, amount) },
                enabled = orderId != null && amount != null,
            ) { Text("Pay") }
        }
    }
}
