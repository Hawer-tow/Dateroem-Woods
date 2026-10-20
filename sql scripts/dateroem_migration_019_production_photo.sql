-- "in the bulk delivery product description can we add a photo instead of
-- only scan with AI since an image will still do fine" - the photo itself
-- (a scaled-down JPEG as a data URL) is kept with the entry.
ALTER TABLE BULK_TRIP_PRODUCTION_DESCRIPTION ADD COLUMN Photo MEDIUMTEXT NULL;
