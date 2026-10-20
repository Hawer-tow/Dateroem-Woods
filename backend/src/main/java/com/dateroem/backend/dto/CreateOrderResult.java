package com.dateroem.backend.dto;

// "place the stkpush directly since its in the sandbox this will allow
// me to test and see if one can really pay using the stk push" -
// stkPushSent tells the website whether to show "check your phone" right
// away; the order's own paymentStatus (from a later GET) reflects what
// actually happened once Safaricom's callback lands.
//
// stkPushError carries Safaricom's own reason when the prompt could not be
// sent (null when it was, or when no phone number was given). Without it
// the website could only ever say "payment isn't automated for this
// order", which reads identically whether the feature is switched off or
// the credentials are wrong - and left the shopper with nothing to act on.
public record CreateOrderResult(int orderId, boolean stkPushSent, String stkPushError) {}
