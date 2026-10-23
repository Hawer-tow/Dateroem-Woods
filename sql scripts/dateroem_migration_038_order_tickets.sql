-- Migration 038 (23 October 2026): order tickets, and sharing the driver
-- with the customer only when staff choose to.
--
-- "ticket number for tracking" - every order gets a short code that can't
-- be guessed, like DW-7K3Q9P. Customers see it on the payment screen, in
-- Ordered Goods and on the Track my order page; staff search by it. The
-- backend makes the code when an order is created and gives one to any
-- older order without one when it starts (OrderTickets.java).
--
-- "vehicle visible only to staff" - the customer used to see the vehicle
-- on every assigned order. Now nothing about the driver or vehicle reaches
-- the customer unless staff switch on Share driver for that delivery; then
-- the customer sees the driver's first name and the number plate, and the
-- company phone number (never the driver's own number).

ALTER TABLE ORDERS
  ADD COLUMN Ticket_Code VARCHAR(12) NULL AFTER Order_ID,
  ADD UNIQUE KEY uq_orders_ticket_code (Ticket_Code);

ALTER TABLE LIGHT_DELIVERIES
  ADD COLUMN Share_Driver TINYINT(1) NOT NULL DEFAULT 0 AFTER Vehicle_ID;
