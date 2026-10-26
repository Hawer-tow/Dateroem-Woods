-- ============================================================================
-- Migration 042: notices (the website's Updates)
-- ============================================================================
-- "a feature not mostly visible but is available in the website like an
-- information area. in the desktop will have that area e.g we might be
-- having an update soon or new features we can write them then we send them
-- with a deadline for elapsing" (26 October 2026).
--
-- Admins write a notice in the desktop app (Notices): a title, the message
-- and the last day it shows (Show_Until, a Kenyan date - it shows through
-- the end of that day, then leaves the website by itself). Is_Published
-- keeps one as a draft; deleting is a soft delete, so a notice can be
-- restored. The website shows the live ones behind a small Updates button
-- in the top bar, which only appears while there is one.
-- ============================================================================

CREATE TABLE NOTICES (
  Notice_ID INT NOT NULL AUTO_INCREMENT,
  Title VARCHAR(120) NOT NULL,
  Message TEXT NOT NULL,
  Show_Until DATE NOT NULL,
  Is_Published BOOLEAN NOT NULL DEFAULT TRUE,
  Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Created_By INT NULL,
  Updated_At DATETIME NULL,
  Updated_By INT NULL,
  Is_Deleted BOOLEAN NOT NULL DEFAULT FALSE,
  Deleted_By INT NULL,
  Deleted_At DATETIME NULL,
  PRIMARY KEY (Notice_ID),
  KEY idx_notices_live (Is_Deleted, Is_Published, Show_Until),
  CONSTRAINT fk_notices_created_by FOREIGN KEY (Created_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_notices_updated_by FOREIGN KEY (Updated_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_notices_deleted_by FOREIGN KEY (Deleted_By) REFERENCES USERS (User_ID)
);
