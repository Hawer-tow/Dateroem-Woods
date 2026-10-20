-- "lets allow both video or image also ... in campaign only one of the
-- medias can be used if its video its video if its image then so but
-- when one wansts to edit you can change from one media to another" -
-- generalizes the campaign's single video slot into a single media slot
-- of either kind, tracked by Media_Type so the app/website know how to
-- render whatever is there.
ALTER TABLE CAMPAIGN CHANGE COLUMN Video Media VARCHAR(250) NULL;
ALTER TABLE CAMPAIGN ADD COLUMN Media_Type VARCHAR(10) NULL AFTER Media;

-- Backfill: every campaign that already had a video is still a video.
UPDATE CAMPAIGN SET Media_Type = 'VIDEO' WHERE Media IS NOT NULL;
