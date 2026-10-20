-- "let allow a user to drop the product if it has not yet been assigned a
-- trip which will allow one to get refunded the money paid for that
-- product but a cost is reduced from the initially sent by the admins who
-- can only do this and after it is dropped ... the stock ... will go to
-- 24" - the cancellation cost and refund figure are kept on the order
-- itself for a real audit trail, same reasoning as every other money
-- column in this schema.
ALTER TABLE ORDERS
  ADD COLUMN Cancellation_Cost DECIMAL(10,2) NULL AFTER Status,
  ADD COLUMN Refunded_Amount DECIMAL(10,2) NULL AFTER Cancellation_Cost,
  ADD COLUMN Cancelled_At DATETIME NULL AFTER Refunded_Amount,
  ADD COLUMN Cancelled_By INT NULL AFTER Cancelled_At;

-- "in mobile when one is delivering a product i want to add a signature
-- area which is stored in the signature blob for the said products for
-- the person to be added to the database" - Signature_Blub already
-- existed but as varchar(250), far too small for a real drawn signature
-- (even a small PNG data URL runs to several KB); widened to hold it.
-- Received_By is new - the signature alone doesn't say whose it is.
ALTER TABLE LIGHT_DELIVERIES
  MODIFY COLUMN Signature_Blub MEDIUMTEXT NULL,
  ADD COLUMN Received_By VARCHAR(250) NULL AFTER Signature_Blub;
