-- ============================================================================
-- Migration 041: retiring a campaign
-- ============================================================================
-- "i wanted to allow a campaign to be retired to just like the products
-- area" (26 October 2026) - the same idea as migration 033 for products.
--
-- Retiring is NOT deleting. A retired campaign:
--   - disappears from the website (the Featured page, the Products page
--     banner, the product tags) and from the website assistant's list of
--     promotions, whatever its Status and dates say,
--   - keeps its picture or video, its products, Status and dates exactly as
--     they are,
--   - still shows in the phone and desktop apps with a "Retired" mark, and
--   - can be brought back at any time (Is_Retired back to 0).
--
-- Separate from Is_Deleted (the bin) on purpose, as for products.
-- ============================================================================

ALTER TABLE CAMPAIGN
  ADD COLUMN Is_Retired  TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN Retired_At  DATETIME   NULL,
  ADD COLUMN Retired_By  INT        NULL,
  ADD CONSTRAINT fk_campaign_retired_by FOREIGN KEY (Retired_By) REFERENCES USERS (User_ID);
