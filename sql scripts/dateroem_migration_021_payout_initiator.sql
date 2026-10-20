-- A B2C result callback comes from Safaricom, not a signed-in user, but
-- recording the payment against its expense (and recalculating the trip's
-- profit) needs to know WHO performed the action. Remember who started the
-- payout, and act as them when M-Pesa reports back.
ALTER TABLE FINANCIAL_TRANSACTION ADD COLUMN Initiated_By INT NULL;
