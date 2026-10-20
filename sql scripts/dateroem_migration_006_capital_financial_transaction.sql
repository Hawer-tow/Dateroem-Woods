-- CAPITAL was missing the Financial_Transaction_ID column every sibling
-- expense/finance table has (TRANSPORT, LOAN, ACCOMODATION, etc.) - a
-- pre-existing gap between business rules.txt's prose ("the capital table
-- is ... connected to the FINANCIAL_TRANSACTION table") and the original
-- CREATE TABLE script, which never actually added the column. Surfaced by
-- TransactionService.candidates() querying it for the first time. Table is
-- empty everywhere so far, safe straight ALTER.
ALTER TABLE CAPITAL ADD COLUMN Financial_Transaction_ID INT NULL AFTER Capital_From_User;
ALTER TABLE CAPITAL ADD CONSTRAINT fk_capital_financial_transaction
    FOREIGN KEY (Financial_Transaction_ID) REFERENCES FINANCIAL_TRANSACTION(Financial_Transaction_ID);
