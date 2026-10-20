-- "allow one to name a delivery when assigning a new one since delivery
-- 10 is ok but it more of a database like but a name delivery maasai
-- allows quick identification" - optional, purely cosmetic label
-- alongside the existing Light_Delivery_ID; bulk deliveries already have
-- this via Delivered_To, light ones never did.
ALTER TABLE LIGHT_DELIVERIES
  ADD COLUMN Delivery_Name VARCHAR(100) NULL AFTER Status;
