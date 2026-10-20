-- Migration 036 (17 October 2026): customers' phone numbers verified by an
-- SMS code.
--
-- "in the registration of a new customer one may give a phone number not
-- theirs ... push a code to the phone number they placed so that its
-- verified ... and abide by laws" - a number counts as the customer's only
-- after they type the code sent to it: at sign-up (password or Google), or,
-- for accounts made before this, the first time they pay. Checkout fills in
-- only a verified number.
--
-- USERS.Phone_Verified_At: when the account's current number was verified
-- (NULL = not verified; cleared whenever the number is changed by hand).
--
-- PHONE_VERIFICATIONS: one row per code sent. The code itself is never
-- stored, only a hash; a correct code at sign-up hands back a one-time
-- "proof" (stored as a hash too) that the sign-up then uses. Rows older than
-- 30 days are deleted every night (PhoneVerificationService) - the number is
-- kept only as long as needed for limits and for checking a sign-up.

ALTER TABLE USERS ADD COLUMN Phone_Verified_At DATETIME NULL AFTER Phone_NO;

CREATE TABLE PHONE_VERIFICATIONS (
  Verification_ID INT NOT NULL AUTO_INCREMENT,
  Phone VARCHAR(15) NOT NULL,
  User_ID INT NULL,
  Code_Hash VARCHAR(100) NOT NULL,
  Attempts INT NOT NULL DEFAULT 0,
  Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Expires_At DATETIME NOT NULL,
  Verified_At DATETIME NULL,
  Proof_Hash CHAR(64) NULL,
  Proof_Used_At DATETIME NULL,
  PRIMARY KEY (Verification_ID),
  KEY idx_phone_verifications_phone (Phone, Created_At),
  KEY idx_phone_verifications_created (Created_At),
  CONSTRAINT fk_phone_verifications_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID)
);
