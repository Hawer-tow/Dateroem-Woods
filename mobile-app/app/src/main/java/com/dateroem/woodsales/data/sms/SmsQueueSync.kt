package com.dateroem.woodsales.data.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.dateroem.woodsales.AppContainer
import com.dateroem.woodsales.data.model.IngestSmsRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SmsQueueSync"
private val ISO_DATE = SimpleDateFormat("yyyy-MM-dd", Locale.US)

// Shared by the live listener (SmsReceiver) and the manual/startup backlog
// scan (resyncInbox) - one parse-and-post path so both behave identically.
object SmsQueueSync {

    // Best-effort, one message. Failures (offline, parse miss, wrong SIM)
    // are swallowed here on purpose - a background listener has nobody to
    // show an error to; resyncInbox() is the safety net for anything this
    // misses while offline, and it reports its own counts back to a screen.
    suspend fun submitIfCompanySim(context: Context, sender: String?, body: String?, subscriptionId: Int, timestampMillis: Long) {
        if (sender == null || !sender.contains("MPESA", ignoreCase = true)) return
        val container = AppContainer(context.applicationContext)
        val companySubscriptionId = container.companySimStore.subscriptionIdOnce() ?: run {
            Log.i(TAG, "No company SIM configured yet - message ignored (set it up under Transactions > M-Pesa SMS Sync)")
            return
        }
        if (subscriptionId != companySubscriptionId) {
            return // The other SIM, if this is a dual-SIM phone - never read.
        }
        val parsed = MpesaSmsParser.parse(body)
        if (parsed == null) {
            Log.w(TAG, "M-Pesa SMS didn't match the expected format - skipped, not guessed")
            return
        }
        try {
            container.api.ingestSms(
                IngestSmsRequest(
                    mpesaReferenceId = parsed.referenceId,
                    amount = parsed.amount,
                    rawSmsText = body,
                    transactionType = parsed.transactionType,
                    mpesaBalance = parsed.balance,
                    simSlot = subscriptionId,
                    receivedAt = ISO_DATE.format(Date(timestampMillis))
                )
            )
        } catch (e: Exception) {
            // Most likely offline - resyncInbox() will pick this exact
            // message up again next time it runs, since it re-scans the
            // inbox itself rather than relying on this call ever succeeding.
            Log.w(TAG, "Couldn't reach mobileServer for one SMS - will be caught by the next resync: ${e.message}")
        }
    }

    data class ResyncResult(
        val scanned: Int,
        val submitted: Int,
        val failed: Int,
        // A handful of the messages that didn't parse, so a person (or
        // whoever's fixing the parser) can actually see what tripped it up
        // instead of just a count - "then what" deserves a real answer.
        val unparsedSamples: List<String> = emptyList()
    )

    // "the resync/backlog-scan covers exactly the offline gap" - re-reads
    // the phone's own SMS inbox (not just what arrived while the app was
    // running) for the company SIM, going back sinceMillis, and posts
    // every M-Pesa message found. Safe to run repeatedly - the backend
    // dedupes on the receipt code.
    suspend fun resyncInbox(context: Context, sinceMillis: Long): ResyncResult {
        val container = AppContainer(context.applicationContext)
        val companySubscriptionId = container.companySimStore.subscriptionIdOnce()
            ?: return ResyncResult(0, 0, 0)

        var scanned = 0
        var submitted = 0
        var failed = 0
        val unparsedSamples = mutableListOf<String>()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID
        )
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} DESC"
        )
        cursor?.use {
            val addressCol = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = it.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = it.getColumnIndex(Telephony.Sms.DATE)
            val subCol = it.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
            while (it.moveToNext()) {
                val address = if (addressCol >= 0) it.getString(addressCol) else null
                if (address == null || !address.contains("MPESA", ignoreCase = true)) continue
                val subscriptionId = if (subCol >= 0) it.getInt(subCol) else -1
                if (subscriptionId != companySubscriptionId) continue
                scanned++
                val body = if (bodyCol >= 0) it.getString(bodyCol) else null
                val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()
                val parsed = MpesaSmsParser.parse(body)
                if (parsed == null) {
                    failed++
                    if (unparsedSamples.size < 8 && body != null) {
                        unparsedSamples.add(body.take(220))
                    }
                    Log.w(TAG, "Couldn't parse M-Pesa SMS: ${body?.take(220)}")
                    continue
                }
                try {
                    container.api.ingestSms(
                        IngestSmsRequest(
                            mpesaReferenceId = parsed.referenceId,
                            amount = parsed.amount,
                            rawSmsText = body,
                            transactionType = parsed.transactionType,
                            mpesaBalance = parsed.balance,
                            simSlot = subscriptionId,
                            receivedAt = ISO_DATE.format(Date(date))
                        )
                    )
                    submitted++
                } catch (e: Exception) {
                    failed++
                }
            }
        }
        container.companySimStore.markResynced()
        return ResyncResult(scanned, submitted, failed, unparsedSamples)
    }
}
