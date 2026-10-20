-- Heavy/bulk order flow: "connect it to the orders table and connect it
-- to the bulk delivery using the id" - a bulk delivery now traces back to
-- the ORDERS row that started the (mostly offline) negotiation, same
-- pattern LIGHT_DELIVERIES already uses. Hard_Cash_Amount/Soft_Cash_Amount
-- split how a bulk payment breaks down (business owner: "some in soft some
-- in cash ... the total in bulk delivery is written all soft and hard") -
-- Amount_Paid stays the combined total. Created_At backs the same
-- candidate-matching-by-time mechanism migration 005 added elsewhere, now
-- that BULK_DELIVERY is a real ExpenseTarget (the soft portion gets
-- matched to an incoming STK payment the same way any other expense does).
ALTER TABLE BULK_DELIVERY ADD COLUMN Order_ID INT NULL AFTER Trip_ID;
ALTER TABLE BULK_DELIVERY ADD CONSTRAINT fk_bulk_delivery_order
    FOREIGN KEY (Order_ID) REFERENCES ORDERS(Order_ID);
ALTER TABLE BULK_DELIVERY ADD COLUMN Hard_Cash_Amount DECIMAL(10,2) NULL AFTER Amount_Paid;
ALTER TABLE BULK_DELIVERY ADD COLUMN Soft_Cash_Amount DECIMAL(10,2) NULL AFTER Hard_Cash_Amount;
ALTER TABLE BULK_DELIVERY ADD COLUMN Created_At DATETIME NULL AFTER Delivered_At;
