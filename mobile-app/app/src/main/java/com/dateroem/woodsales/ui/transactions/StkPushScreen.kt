package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dateroem.woodsales.ui.common.capPhoneNumberInput
import com.dateroem.woodsales.ui.theme.DateroemAccent
import com.dateroem.woodsales.ui.theme.DateroemBackground
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

// STK pulls money FROM a customer - business owner correction: "the stk
// option will only happen to users paying for a product in the product
// payment ... and an exempt for bulk goods also in transaction section".
// Anyone can send one here; there's no category to pre-target (see
// SendMoneyScreen for paying an expense out to a partner instead).
@Composable
fun StkPushScreen(viewModel: StkPushViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DateroemAccent,
        unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DateroemBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp)
    ) {
        Text("Send STK Push", color = DateroemText, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        when (state.step) {
            StkPushStep.SEND -> {
                Text(
                    "Prompts a payment for bulk goods on the customer's phone.",
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::updateAmount,
                    label = { Text("Amount (KES) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
                OutlinedTextField(
                    value = state.phoneNumber,
                    onValueChange = { viewModel.updatePhoneNumber(capPhoneNumberInput(it)) },
                    label = { Text("Phone number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )

                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }

                WoodButton(
                    onClick = { viewModel.send() },
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                    } else {
                        Text("Send Prompt", fontWeight = FontWeight.Bold)
                    }
                }
            }

            StkPushStep.CONFIRM -> {
                Text(
                    if (state.darajaInitiated) {
                        "Live prompt sent to the phone (Transaction #${state.financialTransactionId}). Once the M-Pesa SMS arrives, enter its receipt code to confirm."
                    } else {
                        "Recorded (Transaction #${state.financialTransactionId}) - no live M-Pesa prompt was sent (sandbox not configured yet). Enter a receipt code to confirm it manually for testing."
                    },
                    color = DateroemTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                OutlinedTextField(
                    value = state.referenceId,
                    onValueChange = viewModel::updateReferenceId,
                    label = { Text("M-Pesa receipt code *") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.error != null) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
                WoodButton(
                    onClick = { viewModel.confirm() },
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                    } else {
                        Text("Confirm Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }

            StkPushStep.DONE -> {
                Text(
                    "Confirmed - it's now waiting in the admin assign queue.",
                    color = DateroemAccent,
                    modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)
                )
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}
