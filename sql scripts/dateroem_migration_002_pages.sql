-- ============================================================================
-- Migration 002: schema additions needed for the backend/frontend "pages"
-- described in business rules.txt (the "the backend server:" / "the frontend
-- sever" sections). Run AFTER dateroem_backend_script.sql and
-- dateroem_functions_procedures.sql have already been applied.
-- ============================================================================

-- 1) Concurrency-safe claiming of unassigned FINANCIAL_TRANSACTION rows, so
--    multiple admins working at once split the unassigned pool instead of
--    colliding on the same rows ("handle one user at a time... if 14
--    transactions are unassigned and another user logs in, reduce load to
--    7 for each").
ALTER TABLE FINANCIAL_TRANSACTION
  ADD COLUMN Claimed_By INT NULL,
  ADD COLUMN Claimed_At DATETIME NULL,
  ADD CONSTRAINT fk_financial_transaction_claimed_by
    FOREIGN KEY (Claimed_By) REFERENCES USERS(User_ID);

-- 2) Link marketing campaigns to the specific products they promote
--    ("there will be products and links to the products in the product
--    view page").
CREATE TABLE CAMPAIGN_PRODUCTS (
  Campaign_ID INT NOT NULL,
  Product_ID INT NOT NULL,
  Added_At DATE,

  PRIMARY KEY (Campaign_ID, Product_ID),
  FOREIGN KEY (Campaign_ID) REFERENCES CAMPAIGN(Campaign_ID),
  FOREIGN KEY (Product_ID) REFERENCES PRODUCTS(Product_ID)
);

-- 3) Predefined pickup/delivery areas ("predefined areas in specific cities
--    where we will be delivering the goods so that the user can pick them up").
CREATE TABLE DELIVERY_AREAS (
  Delivery_Area_ID INT AUTO_INCREMENT,
  City VARCHAR(250) NOT NULL,
  Area_Name VARCHAR(250) NOT NULL,
  Description VARCHAR(250),
  Is_Active BOOLEAN NOT NULL DEFAULT TRUE,

  Is_Deleted BOOLEAN NOT NULL DEFAULT FALSE,
  Deleted_By INT,
  Deleted_At DATETIME,

  PRIMARY KEY (Delivery_Area_ID),
  FOREIGN KEY (Deleted_By) REFERENCES USERS(User_ID)
);

DELIMITER $$

-- 4) Product popularity, for the Netflix-style "most ordered" product display.
CREATE FUNCTION fn_product_order_count(p_product_id INT)
RETURNS INT
DETERMINISTIC
READS SQL DATA
BEGIN
  DECLARE v_total INT;
  SELECT COALESCE(SUM(oi.Qty), 0) INTO v_total
  FROM ORDER_ITEMS oi
  JOIN ORDERS o ON o.Order_ID = oi.Order_ID
  WHERE oi.Product_ID = p_product_id
    AND oi.Is_Deleted = FALSE
    AND o.Is_Deleted = FALSE;
  RETURN v_total;
END$$

DELIMITER ;
