-- "if the body feet is above 500 then thats large for a single object ...
-- quantity is not an issue ... adjust for the small ones to be 3 weeks" -
-- Body_Feet_Per_Unit is the size of ONE physical piece of this product
-- (a plank, a pole, ...), independent of how many are in stock or
-- ordered; nothing on PRODUCTS currently records that (Stock is the
-- total inventory, not a single unit's size). NULL means "not measured
-- yet" - treated as the small/short-SLA case until someone fills it in,
-- since assuming "large" by default would silently promise a 3-month
-- wait on products nobody ever classified.
ALTER TABLE PRODUCTS ADD COLUMN Body_Feet_Per_Unit DECIMAL(10,2) NULL AFTER Attributes;

-- "so we track when to schedule the delivery dates ... admin uses this
-- to link it to an outgoing delivery vehicle" - LIGHT_DELIVERIES already
-- models "a delivery tied to an order, assignable to a worker
-- (Worker_ID)"; it was just missing an ETA and a vehicle.
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Estimated_Delivery_Date DATE NULL AFTER Delivered_At;
ALTER TABLE LIGHT_DELIVERIES ADD COLUMN Vehicle_ID INT NULL AFTER Worker_ID;
ALTER TABLE LIGHT_DELIVERIES ADD CONSTRAINT fk_light_deliveries_vehicle
    FOREIGN KEY (Vehicle_ID) REFERENCES VEHICLE(Vehicle_ID);
