-- Migration 035 (17 October 2026): the M-Pesa receipt of a website order's
-- payment in its own column.
--
-- "the payment went through as a real one so why does the database not show
-- the reference or transaction id ... in the desktop" - until now the receipt
-- (e.g. UJH2QAC85N) was only appended to PRODUCT_PAYMENTS.Description
-- (" - receipt ..."), so no screen could show it as a field. ORDERS.Reference_ID
-- is not used for it: that column holds the M-Pesa receipt of a REFUND paid
-- back to the customer (ExpenseTarget.ORDER_REFUND).
--
-- A receipt is never shared by two payments, hence the unique key (NULLs are
-- allowed: payments confirmed by Safaricom's status query, which returns no
-- receipt number, and unpaid ones).

ALTER TABLE PRODUCT_PAYMENTS
  ADD COLUMN Mpesa_Receipt VARCHAR(20) NULL AFTER Provider_Ref,
  ADD UNIQUE KEY uq_product_payments_mpesa_receipt (Mpesa_Receipt);

-- Fill it for payments made before this column existed, from the description.
UPDATE PRODUCT_PAYMENTS
SET Mpesa_Receipt = SUBSTRING_INDEX(SUBSTRING_INDEX(Description, ' - receipt ', -1), ' ', 1)
WHERE Mpesa_Receipt IS NULL
  AND Description LIKE '% - receipt %'
  AND SUBSTRING_INDEX(SUBSTRING_INDEX(Description, ' - receipt ', -1), ' ', 1) REGEXP '^[A-Z0-9]{8,12}$';
