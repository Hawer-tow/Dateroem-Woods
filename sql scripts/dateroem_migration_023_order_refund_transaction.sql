-- "when one drops a product ... where exactly do i go" - dropOrder()
-- (migration 022) only ever wrote the refund figure onto ORDERS itself;
-- there was no FINANCIAL_TRANSACTION-shaped holder for it, so it could
-- never be paid out through the existing Send Money flow the way every
-- other expense category already can. These two columns let ORDERS act
-- as a Send Money target (ExpenseTarget.ORDER_REFUND) exactly like
-- FARMER_PAYMENTS/SALARIES/etc. already do - Reference_ID/
-- Financial_Transaction_ID are the same pair every other target uses to
-- mark "this holder has been paid".
ALTER TABLE ORDERS
  ADD COLUMN Reference_ID VARCHAR(50) NULL AFTER Cancelled_By,
  ADD COLUMN Financial_Transaction_ID INT NULL AFTER Reference_ID;
