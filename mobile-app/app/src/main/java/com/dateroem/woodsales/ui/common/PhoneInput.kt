package com.dateroem.woodsales.ui.common

// "still have an issue with the excess phone number digits keyed more
// than 10 is did not detect this" - the backend rejects a malformed
// number at submit time (MpesaClient.validateAndNormalizePhone), but
// nothing stopped a payout/STK phone field from being typed past a real
// Kenyan number's length in the first place. A real number is at most 12
// digits once normalized (2547XXXXXXXX/2541XXXXXXXX); this caps typing
// there so an extra digit is visibly refused as it's keyed, not just
// rejected later.
fun capPhoneNumberInput(value: String): String {
    if (value.count { it.isDigit() } <= 12) return value
    var result = value
    while (result.count { it.isDigit() } > 12) {
        result = result.dropLast(1)
    }
    return result
}
