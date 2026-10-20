-- The manual "assign a FINANCIAL_TRANSACTION to the closest-matching
-- expense holder by amount + time recorded" workflow (business rules:
-- create a holder e.g. "20 ksh communications", later a real SMS/STK
-- payment of 20 ksh gets matched to it) needs every expense-holder table to
-- carry a real creation timestamp to rank candidates by. Several never had
-- one; SALARIES had one declared as the wrong type (tinyint/boolean) and
-- was therefore unusable. All six tables are empty in every environment so
-- far, so these are safe straight ALTERs with no backfill needed.
ALTER TABLE TRANSPORT ADD COLUMN Created_At DATETIME NULL AFTER Description;
ALTER TABLE COMMUNICATIONS ADD COLUMN Created_At DATETIME NULL AFTER `type`;
ALTER TABLE OTHER_EXPENSES ADD COLUMN Created_At DATETIME NULL AFTER Description;
ALTER TABLE PERMITS ADD COLUMN Created_At DATETIME NULL AFTER End_Date;
ALTER TABLE LOAN ADD COLUMN Created_At DATETIME NULL AFTER Status;
ALTER TABLE SALARIES MODIFY COLUMN Created_At DATETIME NULL;
