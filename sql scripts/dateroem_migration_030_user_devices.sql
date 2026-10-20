-- ============================================================================
-- Migration 030: one approved phone per account
-- ============================================================================
-- "one can have multiple apps on phones ... and login in the same account
-- ... is it possible to add in the database the unique phone id that allows
-- only one user per account per mobile phone ... since one can use two
-- phones and give one to a malicious person" - every mobile-app install
-- creates its own signing key in the phone's secure hardware (Android
-- Keystore; it can never be copied off the phone) and proves it holds that
-- key when signing in. Each account may have ONE active phone; any other
-- phone waits for an admin to approve it (option a). An account's very
-- first phone is approved automatically so existing staff aren't locked out.
--
-- Key_Fingerprint is the SHA-256 (hex) of the public key - how a phone is
-- recognised. Status: PENDING (waiting for an admin), ACTIVE (approved),
-- REVOKED (blocked, replaced or rejected).
--
-- Active_User_ID is filled only while a row is ACTIVE, so its unique index
-- lets the database itself refuse a second active phone for the same
-- account (MySQL allows any number of NULLs under a unique index).
-- ============================================================================

CREATE TABLE USER_DEVICES (
  Device_ID INT NOT NULL AUTO_INCREMENT,
  User_ID INT NOT NULL,
  Key_Fingerprint CHAR(64) NOT NULL,
  Public_Key TEXT NOT NULL,
  Device_Model VARCHAR(120) NULL,
  Os_Version VARCHAR(40) NULL,
  App_Version VARCHAR(40) NULL,
  Status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  Created_At DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  Approved_By INT NULL,
  Approved_At DATETIME NULL,
  Revoked_By INT NULL,
  Revoked_At DATETIME NULL,
  Last_Seen_At DATETIME NULL,
  Active_User_ID INT GENERATED ALWAYS AS (IF(Status = 'ACTIVE', User_ID, NULL)) STORED,
  PRIMARY KEY (Device_ID),
  UNIQUE KEY uq_user_devices_user_key (User_ID, Key_Fingerprint),
  UNIQUE KEY uq_user_devices_one_active (Active_User_ID),
  KEY idx_user_devices_status (Status),
  CONSTRAINT fk_user_devices_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID),
  CONSTRAINT fk_user_devices_approved_by FOREIGN KEY (Approved_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_user_devices_revoked_by FOREIGN KEY (Revoked_By) REFERENCES USERS (User_ID)
);
