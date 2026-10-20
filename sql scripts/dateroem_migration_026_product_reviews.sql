-- "for goods bought by a customer i want to allow them to give ratings to
-- the product so when one comes to buy the product one can see the star
-- reviews" - one rating per customer per product, only for goods actually
-- delivered to them (Order_Items_ID is the purchase that proves it; the
-- backend checks delivery before saving).
--
-- "the user has a choice if the rating be made public or private ... this
-- only happens when one ticks the choice and has to read through the
-- agreement area then tick yes" - private by default. Is_Public only turns
-- on together with the agreement version the customer accepted and when,
-- so consent can always be shown; Public_Name is exactly what they agreed
-- to have displayed (first name + initial, e.g. "Timothy K."). Every
-- change of that choice is also written to AUDIT_LOGS.
--
-- Is_Hidden is admin moderation (abusive/false reviews): hidden reviews
-- drop out of every public figure, and the customer can't unhide them.
CREATE TABLE PRODUCT_REVIEWS (
  Product_Review_ID INT AUTO_INCREMENT PRIMARY KEY,
  Product_ID        INT NOT NULL,
  User_ID           INT NOT NULL,
  Order_Items_ID    INT NOT NULL,
  Rating            TINYINT NOT NULL,
  Comment           VARCHAR(500) NULL,
  Is_Public         BOOLEAN NOT NULL DEFAULT FALSE,
  Public_Name       VARCHAR(60) NULL,
  Consent_Version   VARCHAR(20) NULL,
  Consent_At        DATETIME NULL,
  Is_Hidden         BOOLEAN NOT NULL DEFAULT FALSE,
  Hidden_By         INT NULL,
  Hidden_At         DATETIME NULL,
  Created_At        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Updated_At        DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_product_reviews_rating CHECK (Rating BETWEEN 1 AND 5),
  CONSTRAINT uq_product_reviews_customer UNIQUE (Product_ID, User_ID),
  CONSTRAINT fk_product_reviews_product FOREIGN KEY (Product_ID) REFERENCES PRODUCTS (Product_ID),
  CONSTRAINT fk_product_reviews_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID),
  CONSTRAINT fk_product_reviews_item FOREIGN KEY (Order_Items_ID) REFERENCES ORDER_ITEMS (Order_Items_ID)
);
