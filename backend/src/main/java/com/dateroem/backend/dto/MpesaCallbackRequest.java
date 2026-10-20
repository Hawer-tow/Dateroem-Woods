package com.dateroem.backend.dto;

import java.util.List;

// Shape of Safaricom's Daraja STK push callback POST body - field names
// are exact-case to match their JSON (PascalCase, except the "stkCallback"
// key itself). See TransactionService.handleMpesaCallback().
public record MpesaCallbackRequest(Body Body) {

    public record Body(StkCallback stkCallback) {}

    public record StkCallback(
        String MerchantRequestID,
        String CheckoutRequestID,
        int ResultCode,
        String ResultDesc,
        CallbackMetadata CallbackMetadata
    ) {
        // CallbackMetadata is only present when ResultCode == 0 (success).
        public String itemValue(String name) {
            if (CallbackMetadata == null || CallbackMetadata.Item() == null) {
                return null;
            }
            return CallbackMetadata.Item().stream()
                .filter(item -> name.equals(item.Name()))
                .map(item -> item.Value() == null ? null : item.Value().toString())
                .findFirst()
                .orElse(null);
        }
    }

    public record CallbackMetadata(List<CallbackItem> Item) {}

    public record CallbackItem(String Name, Object Value) {}
}
