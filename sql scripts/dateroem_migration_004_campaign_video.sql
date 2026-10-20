-- Campaigns can now carry a promotional video (relative media key, same
-- scheme as PRODUCTS.Image) - business ask: "add a feature to add a
-- campaign with a video ... later in the lifecycle we can connect it to
-- the medias e.g tiktok for marketing but for now let it connect to the
-- campaign in the website."
ALTER TABLE CAMPAIGN ADD COLUMN Video VARCHAR(250) NULL AFTER Status;
