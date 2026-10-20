-- ============================================================================
-- dateroem: server-load-reduction layer
-- Scalar functions pre-compute per-trip totals so the backend can pull an
-- already-calculated value (SELECT fn_trip_profit(5)) instead of summing
-- raw rows in application code. One procedure composes them and writes the
-- result into PROFIT + logs the recalculation to AUDIT_LOGS.
--
-- Run this against the dateroem database AFTER the table-creation script
-- (business rules.txt) has already been executed.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Migration: PROFIT needs exactly one row per trip so sp_recalculate_trip_profit
-- can upsert instead of accumulating duplicate rows on every recalculation.
-- (business rules.txt's PROFIT definition has been updated to include this
-- for future fresh installs.)
-- ----------------------------------------------------------------------------
ALTER TABLE PROFIT ADD UNIQUE KEY uq_profit_trip (Trip_ID);

DELIMITER $$

-- ============================================================================
-- SECTION 1: per-category expense totals (business rules "simplified calculations")
-- ============================================================================

CREATE FUNCTION fn_trip_loan_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM LOAN
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_capital_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM CAPITAL
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

-- Farmer payments have no Trip_ID of their own; they're reached via the
-- farmers who supplied trees on this trip (TREES.Farmer_ID), per the
-- business-rules formula TREES(Trip_ID).FARMER_PAYMENTS(Farmer_ID).amount.
-- Note: a farmer who supplies trees to more than one trip has their payment
-- counted toward every one of those trips -- inherent to this link, not a bug.
CREATE FUNCTION fn_trip_farmer_payment_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(fp.Amount), 0) INTO v_total
  FROM FARMER_PAYMENTS fp
  WHERE fp.Is_Deleted = FALSE
    AND fp.Farmer_ID IN (
      SELECT DISTINCT t.Farmer_ID
      FROM TREES t
      WHERE t.Trip_ID = p_trip_id AND t.Is_Deleted = FALSE
    );
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_salary_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM SALARIES
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_accomodation_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM ACCOMODATION
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_transport_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM TRANSPORT
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_communication_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM COMMUNICATIONS
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_machine_expense_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM MACHINE_EXPENSES
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_other_expense_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM OTHER_EXPENSES
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_permit_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM PERMITS
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

-- ============================================================================
-- SECTION 2: income totals
-- ============================================================================

CREATE FUNCTION fn_trip_product_payment_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount), 0) INTO v_total
  FROM PRODUCT_PAYMENTS
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_bulk_delivery_total(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(Amount_Paid), 0) INTO v_total
  FROM BULK_DELIVERY
  WHERE Trip_ID = p_trip_id AND Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_total_income(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  RETURN fn_trip_product_payment_total(p_trip_id) + fn_trip_bulk_delivery_total(p_trip_id);
END$$

-- ============================================================================
-- SECTION 3: aggregate expense total
-- ============================================================================

CREATE FUNCTION fn_trip_total_expenses(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  RETURN
      fn_trip_loan_total(p_trip_id)
    + fn_trip_capital_total(p_trip_id)
    + fn_trip_farmer_payment_total(p_trip_id)
    + fn_trip_salary_total(p_trip_id)
    + fn_trip_accomodation_total(p_trip_id)
    + fn_trip_transport_total(p_trip_id)
    + fn_trip_communication_total(p_trip_id)
    + fn_trip_machine_expense_total(p_trip_id)
    + fn_trip_other_expense_total(p_trip_id)
    + fn_trip_permit_total(p_trip_id);
END$$

-- ============================================================================
-- SECTION 4: transaction-cost simplification
-- (each expense row's Reference_ID / unique_Salary_Id is matched against
-- FINANCIAL_TRANSACTION.Reference_ID to pull the Mpesa/paybill transaction
-- fee that was charged for that specific payment)
-- ============================================================================

CREATE FUNCTION fn_trip_capital_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM CAPITAL c
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = c.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE c.Trip_ID = p_trip_id AND c.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_farmer_payment_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM FARMER_PAYMENTS fp
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = fp.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE fp.Is_Deleted = FALSE
    AND fp.Farmer_ID IN (
      SELECT DISTINCT t.Farmer_ID
      FROM TREES t
      WHERE t.Trip_ID = p_trip_id AND t.Is_Deleted = FALSE
    );
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_salary_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM SALARIES s
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = s.unique_Salary_Id AND ft.Is_Deleted = FALSE
  WHERE s.Trip_ID = p_trip_id AND s.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_accomodation_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM ACCOMODATION a
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = a.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE a.Trip_ID = p_trip_id AND a.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_transport_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM TRANSPORT tr
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = tr.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE tr.Trip_ID = p_trip_id AND tr.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_communication_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM COMMUNICATIONS co
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = co.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE co.Trip_ID = p_trip_id AND co.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_machine_expense_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM MACHINE_EXPENSES me
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = me.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE me.Trip_ID = p_trip_id AND me.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_other_expense_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM OTHER_EXPENSES oe
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = oe.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE oe.Trip_ID = p_trip_id AND oe.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_permit_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM PERMITS pe
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = pe.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE pe.Trip_ID = p_trip_id AND pe.Is_Deleted = FALSE;
  RETURN v_total;
END$$

-- Loan repayment tx cost is reached through LOAN -> LOAN_REPAYMENTS, not
-- through LOAN's own Reference_ID, per the business-rules formula.
CREATE FUNCTION fn_trip_loan_repayment_tx_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total DECIMAL(12,2);
  SELECT COALESCE(SUM(ft.Transaction_Cost), 0) INTO v_total
  FROM LOAN l
  JOIN LOAN_REPAYMENTS lr
    ON lr.Loan_ID = l.Loan_ID AND lr.Is_Deleted = FALSE
  JOIN FINANCIAL_TRANSACTION ft
    ON ft.Reference_ID = lr.Reference_ID AND ft.Is_Deleted = FALSE
  WHERE l.Trip_ID = p_trip_id AND l.Is_Deleted = FALSE;
  RETURN v_total;
END$$

CREATE FUNCTION fn_trip_total_transaction_cost(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  RETURN
      fn_trip_loan_repayment_tx_cost(p_trip_id)
    + fn_trip_capital_tx_cost(p_trip_id)
    + fn_trip_farmer_payment_tx_cost(p_trip_id)
    + fn_trip_salary_tx_cost(p_trip_id)
    + fn_trip_accomodation_tx_cost(p_trip_id)
    + fn_trip_transport_tx_cost(p_trip_id)
    + fn_trip_communication_tx_cost(p_trip_id)
    + fn_trip_machine_expense_tx_cost(p_trip_id)
    + fn_trip_other_expense_tx_cost(p_trip_id)
    + fn_trip_permit_tx_cost(p_trip_id);
END$$

-- ============================================================================
-- SECTION 5: profit method
-- profit = total income - total expenses - total transaction cost
-- ============================================================================

CREATE FUNCTION fn_trip_profit(p_trip_id INT)
RETURNS DECIMAL(12,2)
DETERMINISTIC
READS SQL DATA
BEGIN
  RETURN
      fn_trip_total_income(p_trip_id)
    - fn_trip_total_expenses(p_trip_id)
    - fn_trip_total_transaction_cost(p_trip_id);
END$$

-- ============================================================================
-- SECTION 6: orchestrating procedure
-- Call this once the backend has finished overwriting/allocating expense
-- rows for a trip. Upserts PROFIT.Recorded_Amount and logs the recalculation.
-- ============================================================================

CREATE PROCEDURE sp_recalculate_trip_profit(
  IN p_trip_id INT,
  IN p_actor_user_id INT
)
BEGIN
  DECLARE v_profit DECIMAL(12,2);

  IF p_actor_user_id IS NULL THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'p_actor_user_id is required: every write must record who performed it';
  END IF;

  SET v_profit = fn_trip_profit(p_trip_id);

  INSERT INTO PROFIT (Trip_ID, Recorded_Amount)
  VALUES (p_trip_id, v_profit)
  ON DUPLICATE KEY UPDATE Recorded_Amount = v_profit;

  INSERT INTO AUDIT_LOGS (Target_Table, Target_Id, Payload, Created_At, Actor_ID)
  VALUES (
    'PROFIT',
    p_trip_id,
    CONCAT('Recalculated profit for Trip_ID=', p_trip_id, ' -> ', v_profit),
    CURDATE(),
    p_actor_user_id
  );

  SELECT v_profit AS Recorded_Amount;
END$$

DELIMITER ;

-- ============================================================================
-- Usage from the backend:
--   SELECT fn_trip_profit(5);                    -- read-only, no write
--   CALL sp_recalculate_trip_profit(5, 12);       -- writes PROFIT + AUDIT_LOGS
-- ============================================================================
