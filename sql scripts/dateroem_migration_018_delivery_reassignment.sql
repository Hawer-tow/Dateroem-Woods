-- "when a delivery is being marked as delivered any product that was not
-- delivered for one or more issues ... will be dropped for reassignment in
-- another delivery and can be highlighted in another color to show their
-- high priority" - an order that missed its delivery goes back to the
-- unassigned pool flagged as priority, with the reason it was missed.
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Needs_Reassignment TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Undelivered_Reason VARCHAR(250) NULL;
