-- "for each delivery area in the database lets add a field to add like
-- google maps coordinates of the location of that area" - same "lat,long"
-- text convention TREES.Coordinates already uses (migration 022's
-- neighbor columns), so an admin can find a spot on Google Maps, copy the
-- coordinates and paste them straight in. Powers both the desktop area
-- form and the website's "delivery areas near you" / "view on maps".
ALTER TABLE DELIVERY_AREAS
  ADD COLUMN Coordinates VARCHAR(250) NULL AFTER Description;
