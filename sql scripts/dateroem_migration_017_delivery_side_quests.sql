-- "a light delivery can be fixed to a bulk delivery as a side quest ...
-- since bulk delivery still has space to do this or time" - a light order
-- can ride along with a specific bulk delivery, and light deliveries can
-- be grouped under another light delivery, so an order is linked to a
-- specific delivery instead of only to the trip as a whole.
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Bulk_Delivery_ID INT NULL AFTER Trip_ID;
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Parent_Light_Delivery_ID INT NULL AFTER Bulk_Delivery_ID;
ALTER TABLE LIGHT_DELIVERIES
    ADD CONSTRAINT fk_light_deliveries_bulk FOREIGN KEY (Bulk_Delivery_ID) REFERENCES BULK_DELIVERY(Bulk_Delivery_ID);
ALTER TABLE LIGHT_DELIVERIES
    ADD CONSTRAINT fk_light_deliveries_parent FOREIGN KEY (Parent_Light_Delivery_ID) REFERENCES LIGHT_DELIVERIES(Light_Delivery_ID);
