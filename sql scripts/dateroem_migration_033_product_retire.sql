-- ============================================================================
-- Migration 033: retiring a product
-- ============================================================================
-- "i want to add a feature such that one can retire a product in the site
-- from desktop or mobile ... despite the stock being there or in an event
-- where the stock is depleted ... the product can be retired yes but if one
-- had ordered parts of the product then those are not affected ... but it
-- can be brought back incase it was being reviewd from a bad ratin or
-- comment."
--
-- Retiring is NOT deleting. A retired product:
--   - disappears from the customer shop and can no longer be ordered,
--   - keeps its stock and all its existing orders exactly as they are
--     (nothing here touches ORDERS/ORDER_ITEMS), so parts already ordered
--     are unaffected,
--   - still shows to admins (desktop/mobile) with a "Retired" mark, and
--   - can be brought back at any time (Is_Retired back to 0).
--
-- Separate from Is_Deleted (soft delete) on purpose: a product can be
-- retired while very much "alive", and brought back, without ever entering
-- the deleted-products bin.
-- ============================================================================

ALTER TABLE PRODUCTS
  ADD COLUMN Is_Retired  TINYINT(1) NOT NULL DEFAULT 0 AFTER Stock,
  ADD COLUMN Retired_At  DATETIME   NULL        AFTER Is_Retired,
  ADD COLUMN Retired_By  INT        NULL        AFTER Retired_At,
  ADD CONSTRAINT fk_products_retired_by FOREIGN KEY (Retired_By) REFERENCES USERS (User_ID);

-- Admin views look up retired products and the "needs attention" list
-- (in stock = 0, still active) often; this keeps those filters quick.
CREATE INDEX idx_products_retired ON PRODUCTS (Is_Retired, Is_Deleted);
