package com.dateroem.woodsales.data.sms

// Parses a Safaricom M-Pesa confirmation SMS body into the fields
// SmsQueueService needs. Real examples this is built against:
//   "TFG5H6J7K Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 20/9/26
//    at 2:30 PM. New M-PESA balance is Ksh12,345.00. Transaction cost, Ksh0.00."
//   "QAB1C2D3E Confirmed.You have received Ksh1,000.00 from JANE DOE
//    0722334455 on 20/9/26 at 9:15 AM New M-PESA balance is Ksh5,678.00."
//   "RCD4E5F6G Confirmed. Ksh2,000.00 paid to ACME HARDWARE. on 20/9/26 at
//    11:00 AM. New M-PESA balance is Ksh8,000.00."
//   "SEF7G8H9I Confirmed. Ksh1,500.00 withdrawn from ... New M-PESA balance is Ksh500.00."
// Deliberately conservative: returns null (not a best-effort guess) if the
// receipt code or amount can't be found - "this must be done in care", a
// message that doesn't parse cleanly is better skipped than queued wrong.
object MpesaSmsParser {

    data class Parsed(
        val referenceId: String,
        val amount: Double,
        val transactionType: String,
        val balance: Double?
    )

    // The receipt code is the first token, always 10 chars, letters+digits,
    // immediately followed by " Confirmed".
    private val RECEIPT_REGEX = Regex("""^([A-Z0-9]{10})\s+Confirmed""")
    private val AMOUNT_REGEX = Regex("""Ksh\s?([\d,]+\.\d{2})""")
    private val BALANCE_REGEX = Regex("""balance is Ksh\s?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)

    fun parse(body: String?): Parsed? {
        if (body.isNullOrBlank()) return null
        val text = body.trim()

        val receiptMatch = RECEIPT_REGEX.find(text) ?: return null
        val referenceId = receiptMatch.groupValues[1]

        val amountMatch = AMOUNT_REGEX.find(text) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null

        val balance = BALANCE_REGEX.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        val type = when {
            text.contains("sent to", ignoreCase = true) -> "SENT"
            text.contains("received from", ignoreCase = true) -> "RECEIVED"
            text.contains("paid to", ignoreCase = true) -> "PAID"
            text.contains("withdraw", ignoreCase = true) -> "WITHDRAW"
            text.contains("airtime", ignoreCase = true) -> "AIRTIME"
            else -> "OTHER"
        }

        return Parsed(referenceId, amount, type, balance)
    }
}
