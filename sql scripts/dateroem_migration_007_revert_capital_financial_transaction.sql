-- Reverts migration 006. Correction from the business owner: CAPITAL is
-- money an admin fills in directly before a trip begins (pre-trip company
-- funds), not a holder matched against an incoming SMS/STK payment like
-- TRANSPORT/COMMUNICATIONS/etc - it belongs entirely to the admin-only
-- Loans & Finance page and is exempt from the FINANCIAL_TRANSACTION
-- candidate-matching workflow. Table is empty everywhere so far.
ALTER TABLE CAPITAL DROP FOREIGN KEY fk_capital_financial_transaction;
ALTER TABLE CAPITAL DROP COLUMN Financial_Transaction_ID;
