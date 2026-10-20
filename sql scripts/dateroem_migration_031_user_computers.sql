-- ============================================================================
-- Migration 031: approved computers for the desktop app
-- ============================================================================
-- "i will want the desktop to operate like the mobile such that its now
-- connected ... to the backend but a standalone application that has higher
-- privilege that can work even if the backend is not in the same machine
-- ... allow one to share it as an application to other admins" - the
-- desktop app's counterpart to USER_DEVICES (migration 030). On first
-- sign-in the app creates its own signing key in Windows' key store (it can
-- never be exported, so copying the app to another computer does not copy
-- the key) and proves it holds that key every time it signs in.
--
-- Each account may have ONE approved computer; any other computer waits for
-- the SuperAdmin to approve it ("the one who permits phones desktops is the
-- superadmin"). Unlike phones, an Admin's first computer is not approved by
-- itself (a stolen admin password must not be enough) - only the
-- SuperAdmin's own first computer is, since nobody else could approve it.
--
-- Same columns and rules as USER_DEVICES: Key_Fingerprint is the SHA-256
-- (hex) of the public key; Status: PENDING, ACTIVE, REVOKED; Active_User_ID
-- is filled only while a row is ACTIVE, so its unique index lets the
-- database itself refuse a second approved computer for the same account.
-- ============================================================================

CREATE TABLE USER_COMPUTERS (
  Computer_ID INT NOT NULL AUTO_INCREMENT,
  User_ID INT NOT NULL,
  Key_Fingerprint CHAR(64) NOT NULL,
  Public_Key TEXT NOT NULL,
  Computer_Name VARCHAR(120) NULL,
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
  PRIMARY KEY (Computer_ID),
  UNIQUE KEY uq_user_computers_user_key (User_ID, Key_Fingerprint),
  UNIQUE KEY uq_user_computers_one_active (Active_User_ID),
  KEY idx_user_computers_status (Status),
  CONSTRAINT fk_user_computers_user FOREIGN KEY (User_ID) REFERENCES USERS (User_ID),
  CONSTRAINT fk_user_computers_approved_by FOREIGN KEY (Approved_By) REFERENCES USERS (User_ID),
  CONSTRAINT fk_user_computers_revoked_by FOREIGN KEY (Revoked_By) REFERENCES USERS (User_ID)
);
