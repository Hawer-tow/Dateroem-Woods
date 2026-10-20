-- "when you pay it it asks for a receipt isnt this supposed to fill itself" -
-- an outgoing payment now carries its own progress, so the apps can show
-- "sending... / paid, receipt X / failed, because Y" instead of asking a
-- person to type the M-Pesa receipt in.
ALTER TABLE FINANCIAL_TRANSACTION ADD COLUMN Status VARCHAR(20) NULL;
ALTER TABLE FINANCIAL_TRANSACTION ADD COLUMN Status_Reason VARCHAR(250) NULL;
ALTER TABLE FINANCIAL_TRANSACTION ADD COLUMN Initiated_At DATETIME NULL;
