package com.dateroem.woodsales.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dateroem.woodsales.ui.theme.DateroemSurface
import com.dateroem.woodsales.ui.theme.DateroemText
import com.dateroem.woodsales.ui.theme.DateroemTextMuted
import com.dateroem.woodsales.ui.theme.WoodButton
import com.dateroem.woodsales.ui.theme.WoodOn

private val SuccessGreen = Color(0xFF4FA35C)

// The body of every payout screen. The amount and recipient come filled in;
// the person presses Send, watches it go through, and is told it worked.
@Composable
fun PayoutContent(controller: PayoutController, purpose: String, sendLabel: String, onDone: () -> Unit) {
    val state by controller.state.collectAsState()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = DateroemAccent,
        unfocusedBorderColor = DateroemTextMuted.copy(alpha = 0.4f)
    )

    when (state.step) {
        PayoutStep.SEND -> if (state.submitting) {
            TransferAnimation(state.paidAmount, state.recipientName, stage = 0, waitedSeconds = 0)
        } else {
            val showFields = !state.prefilled || state.editing
            if (state.prefilled) {
                // Everything worked out from the entry - a summary, not a form.
                Column(
                    modifier = Modifier.fillMaxWidth().background(DateroemSurface, RoundedCornerShape(14.dp)).padding(16.dp)
                ) {
                    Text("Amount", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
                    Text("KES ${state.amount}", color = DateroemAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("To", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                    Text(
                        listOfNotNull(state.recipientName, state.phoneNumber).joinToString("  ·  "),
                        color = DateroemText, fontWeight = FontWeight.SemiBold
                    )
                    Text("From the company M-Pesa wallet", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                }
                TextButton(onClick = { controller.toggleEditing() }) { Text(if (state.editing) "Hide details" else "Change amount or number") }
            }
            if (showFields) {
                OutlinedTextField(
                    value = state.amount,
                    onValueChange = controller::updateAmount,
                    label = { Text("Amount (KES) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = state.phoneNumber,
                    onValueChange = { controller.updatePhoneNumber(capPhoneNumberInput(it)) },
                    label = { Text("Recipient phone number *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }
            if (state.sandbox) {
                Text(
                    "Sandbox mode: M-Pesa's test environment is in use, so no real money moves.",
                    color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                )
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            WoodButton(
                onClick = { controller.send() },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (state.submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                else Text("Review payment", fontWeight = FontWeight.Bold)
            }
        }

        PayoutStep.CONFIRM -> Column(
            modifier = Modifier.fillMaxWidth().border(2.dp, SuccessGreen, RoundedCornerShape(14.dp))
                .background(DateroemSurface, RoundedCornerShape(14.dp)).padding(18.dp)
        ) {
            Text("Confirm before sending", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "For security, check every detail below. Money sent to the wrong number cannot be brought back.",
                color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            ConfirmLine("Amount", "KES ${state.paidAmount}", big = true)
            ConfirmLine("Send to", state.recipientName ?: "Recipient not named")
            ConfirmLine("Phone number", state.phoneNumber.trim())
            ConfirmLine("For", purpose)
            ConfirmLine("Paid from", "Company M-Pesa wallet")
            if (state.sandbox) {
                Text(
                    "Sandbox mode: no real money moves.",
                    color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                )
            }
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(top = 14.dp)
            ) {
                Checkbox(
                    checked = state.acknowledged,
                    onCheckedChange = controller::setAcknowledged,
                    colors = CheckboxDefaults.colors(checkedColor = SuccessGreen)
                )
                Text(
                    "I have checked the amount and phone number and they are correct.",
                    color = DateroemText, style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = { controller.confirmAndSend() },
                enabled = state.acknowledged,
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Confirm & $sendLabel", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { controller.backToEdit() }, modifier = Modifier.fillMaxWidth()) { Text("Go back and change") }
        }

        PayoutStep.SENDING -> TransferAnimation(state.paidAmount, state.recipientName, stage = 1, waitedSeconds = state.waitedSeconds)

        PayoutStep.DONE -> Column(
            modifier = Modifier.fillMaxWidth().background(SuccessGreen.copy(alpha = 0.16f), RoundedCornerShape(14.dp)).padding(20.dp)
        ) {
            SuccessSeal()
            Text("Payment sent", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally))
            Text(
                "KES ${state.paidAmount.ifBlank { state.amount }}" + (state.recipientName?.let { " to $it" } ?: "") + " for $purpose.",
                color = DateroemText, modifier = Modifier.padding(top = 8.dp)
            )
            state.receipt?.let {
                Text("M-Pesa receipt", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                Text(it, color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Text("It has been recorded against this entry.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            if (state.sandbox) {
                Text("Sandbox payout - no real money moved.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            WoodButton(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Done", fontWeight = FontWeight.Bold) }
        }

        PayoutStep.FAILED -> Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f), RoundedCornerShape(14.dp)).padding(20.dp)
        ) {
            Text("The payment didn't go through", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(state.failureReason ?: "M-Pesa didn't complete the payout.", color = DateroemText, modifier = Modifier.padding(top = 8.dp))
            Text("Nothing was recorded against the entry, so you can try again.", color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            WoodButton(
                onClick = { controller.tryAgain() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Try again", fontWeight = FontWeight.Bold) }
        }

        PayoutStep.TIMED_OUT -> Column(
            modifier = Modifier.fillMaxWidth().background(DateroemSurface, RoundedCornerShape(14.dp)).padding(20.dp)
        ) {
            Text("Still waiting for M-Pesa", color = DateroemText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "The payout was sent but M-Pesa hasn't reported back yet. Check again in a moment - if the M-Pesa callback tunnel " +
                    "isn't running, its answer can't reach the system.",
                color = DateroemTextMuted, modifier = Modifier.padding(top = 8.dp)
            )
            WoodButton(
                onClick = { controller.checkAgain() },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text("Check again", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { controller.showManual() }) { Text("I already have the M-Pesa receipt") }
        }

        PayoutStep.MANUAL -> {
            Text(
                if (state.transactionId != null) {
                    "Recorded (Transaction #${state.transactionId}). Live M-Pesa payouts aren't switched on, so confirm it with the receipt code."
                } else "Enter the M-Pesa receipt code to confirm.",
                color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = state.manualReference,
                onValueChange = controller::updateManualReference,
                label = { Text("M-Pesa receipt code *") },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            WoodButton(
                onClick = { controller.confirmManual() },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                if (state.submitting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = WoodOn)
                else Text("Confirm Payment", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = { controller.checkAgain() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Check M-Pesa again instead") }
        }
    }
}

@Composable
private fun ConfirmLine(label: String, value: String, big: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(label, color = DateroemTextMuted, style = MaterialTheme.typography.bodySmall)
        Text(
            value, color = DateroemText, fontWeight = FontWeight.ExtraBold,
            fontSize = if (big) 26.sp else 17.sp
        )
    }
}
