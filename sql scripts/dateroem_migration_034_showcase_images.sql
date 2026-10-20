-- ============================================================================
-- Migration 034: showcase pictures (Bulk Orders page)
-- ============================================================================
-- "add a picture ... in the bulk order ... not features just pictures that
-- can be used to show case what bulk orders look like so that customers can
-- see a template of what is meant by bulk orders ... like a slide of them
-- ... one can delete or add them ... this is not like the product pictures.
-- this are rarely modified" - and, from the owner on who may edit them:
-- "a marketer admin or superadmin can edit it".
--
-- Separate from PRODUCTS on purpose: these pictures are never for sale and
-- never appear anywhere but their own slideshow. The image itself lives in
-- media/showcase/ (Image holds the key, as PRODUCTS.Image does); Caption is
-- the optional line under it; Sort_Order is the slideshow order. Section
-- names the page a picture belongs to - only 'bulk_orders' today.
--
-- Soft delete like every other table: a removed picture leaves the
-- slideshow but its row (and file) stay, with who removed it and when.
-- ============================================================================

CREATE TABLE SHOWCASE_IMAGES (
  Showcase_ID INT NOT NULL AUTO_INCREMENT,
  Section VARCHAR(30) NOT NULL DEFAULT 'bulk_orders',
  Image VARCHAR(250) NOT NULL,
  Caption VARCHAR(120) NULL,
  Sort_Order INT NOT NULL DEFAULT 0,
  Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Created_By INT NULL,
  Is_Deleted TINYINT(1) NOT NULL DEFAULT 0,
  Deleted_By INT NULL,
  Deleted_At DATETIME NULL,
  PRIMARY KEY (Showcase_ID),
  KEY idx_showcase_section (Section, Is_Deleted, Sort_Order),
  CONSTRAINT fk_showcase_created_by FOREIGN KEY (Created_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_showcase_deleted_by FOREIGN KEY (Deleted_By) REFERENCES USERS (User_ID)
);
