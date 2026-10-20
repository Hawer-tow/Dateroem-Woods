package com.dateroem.backend.dto;

import java.math.BigDecimal;

// STK push pulls money FROM a customer TO the business - business owner
// correction: it's only for a customer paying for a product (website) or a
// bulk-goods payment (mobile Transactions section, any user can send one).
// It never pre-targets an expense category - expenses are money the
// business pays OUT to a partner, which is SendMoneyRequest's job instead.
public record StkPushRequest(BigDecimal amount, String phoneNumber) {}
