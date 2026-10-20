-- Safaricom's Daraja STK push flow is async: initiating a prompt returns a
-- CheckoutRequestID immediately, then their callback (posted later, out of
-- band) reports the result against that same ID - there was nowhere to
-- store it to correlate the callback back to the right
-- FINANCIAL_TRANSACTION row. "create the pathways correctly for testing
-- later we will then fill the correct credentials so now we do the
-- sandbox testing then later we go production."
ALTER TABLE FINANCIAL_TRANSACTION ADD COLUMN Checkout_Request_ID VARCHAR(250) NULL AFTER Sms_Queue_ID;
CREATE INDEX idx_financial_transaction_checkout_request_id ON FINANCIAL_TRANSACTION (Checkout_Request_ID);
