-- "i forgot to connect the shiiping to the actual delivery are table" -
-- checkout now records a real FK to DELIVERY_AREAS instead of a free-typed
-- city string, so the pickup choice a customer confirms at checkout is the
-- same record LIGHT_DELIVERIES/staff work from.
ALTER TABLE ORDERS ADD COLUMN Delivery_Area_ID INT NULL AFTER Hotspot_City;
ALTER TABLE ORDERS ADD CONSTRAINT fk_orders_delivery_area
    FOREIGN KEY (Delivery_Area_ID) REFERENCES DELIVERY_AREAS(Delivery_Area_ID);
