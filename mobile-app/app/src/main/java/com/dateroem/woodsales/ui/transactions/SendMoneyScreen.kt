package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted

// "Send Money" pays a specific expense holder out to a partner - business
// owner correction: "expenses are payments done to the other partners ...
// rather a send money option". Admin-only entry point (from the Expense
// List's Pay action). The amount and who it goes to are filled in from the
// entry; pressing Send is all that's left, and the M-Pesa receipt fills
// itself in when the payout lands.
@Composable
fun SendMoneyScreen(viewModel: SendMoneyViewModel, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DateroemBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        Text("Send Money", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "Paying: ${viewModel.categoryLabel}",
            color = DateroemTextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        PayoutContent(viewModel.flow, purpose = viewModel.categoryLabel, sendLabel = "Send Money", onDone = onDone)
    }
}
