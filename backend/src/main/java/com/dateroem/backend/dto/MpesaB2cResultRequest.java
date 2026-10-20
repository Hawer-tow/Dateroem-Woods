package com.dateroem.backend.dto;

import java.util.List;

// Shape of Safaricom's Daraja B2C result callback POST body (also used for
// the QueueTimeOutURL callback - same envelope). Field names are exact-
// case to match their JSON. See TransactionService.handleB2CResult().
public record MpesaB2cResultRequest(Result Result) {

    public record Result(
        int ResultType,
        int ResultCode,
        String ResultDesc,
        String OriginatorConversationID,
        String ConversationID,
        String TransactionID,
        ResultParameters ResultParameters
    ) {
        // ResultParameters is only present when ResultCode == 0 (success).
        public String paramValue(String key) {
            if (ResultParameters == null || ResultParameters.ResultParameter() == null) {
                return null;
            }
            return ResultParameters.ResultParameter().stream()
                .filter(param -> key.equals(param.Key()))
                .map(param -> param.Value() == null ? null : param.Value().toString())
                .findFirst()
                .orElse(null);
        }
    }

    public record ResultParameters(List<ResultParameter> ResultParameter) {}

    public record ResultParameter(String Key, Object Value) {}
}
