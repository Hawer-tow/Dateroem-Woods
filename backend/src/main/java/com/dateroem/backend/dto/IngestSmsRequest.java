package com.dateroem.backend.dto;

import java.math.BigDecimal;

// One M-Pesa SMS off the company-SIM phone. "the phone has the message app
// which have the mpesa messages coming in each with a transaction id ...
// i want the system to check the messages compare" - mpesaReferenceId is
// that transaction id (e.g. "UIJ030ETLH"), parsed on the phone; the raw
// text is kept too so an admin assigning it later can eyeball it against
// what M-Pesa actually said rather than trusting the parse blindly.
public record IngestSmsRequest(
    String mpesaReferenceId,
    BigDecimal amount,
    String rawSmsText,
    String transactionType,
    BigDecimal mpesaBalance,
    Integer simSlot,
    String receivedAt
) {}
