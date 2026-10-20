package com.dateroem.woodsales.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Manifest-registered (not runtime-registered) so this still fires even
// when the app isn't running - "the message goes into the message app"
// while the app may well be closed. RECEIVE_SMS makes every app holding it
// get this broadcast regardless of which app is the phone's default SMS
// app, so nothing about that setting needs to change.
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Dual-SIM phones carry the subscription that received this
        // message under this extra (the de-facto standard key since
        // Android's own dual-SIM support landed - not part of
        // SmsMessage itself). Absent on single-SIM devices, where -1
        // just means "the only SIM there is".
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val sender = messages.first().originatingAddress
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val timestamp = messages.first().timestampMillis

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsQueueSync.submitIfCompanySim(context, sender, body, subscriptionId, timestamp)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
