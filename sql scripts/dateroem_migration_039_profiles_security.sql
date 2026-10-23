-- Migration 039 (23 October 2026): profiles, passwords and the idle lock.
--
-- "updating contacts/password ... is it proper to allow the superadmin to
-- see actual passwords" - nobody can (passwords are one-way BCrypt hashes).
-- Instead people change their own password, and an admin can reset one to a
-- temporary password that must be changed at the next sign-in.
--
-- USERS.Photo: the profile photo's media key (profiles/u<id>-<random>.jpg),
--   or NULL for the initial letter.
-- USERS.Must_Change_Password: set by an admin's reset; the account can do
--   nothing but choose a new password until it is cleared.
-- USERS.Password_Changed_At: sign-ins made before this moment are refused,
--   so changing or resetting a password signs the account out everywhere
--   else.
--
-- APP_SETTINGS: settings that belong to the whole business, kept on the
-- server so every phone and computer follows them - first the idle lock's
-- minutes ("a timer like about 25 minutes of inactivity"), set by the
-- SuperAdmin.

ALTER TABLE USERS
  ADD COLUMN Photo VARCHAR(120) NULL AFTER Theme,
  ADD COLUMN Must_Change_Password TINYINT(1) NOT NULL DEFAULT 0 AFTER Photo,
  ADD COLUMN Password_Changed_At DATETIME NULL AFTER Must_Change_Password;

CREATE TABLE APP_SETTINGS (
  Setting_Key VARCHAR(64) NOT NULL,
  Setting_Value VARCHAR(2000) NULL,
  Updated_By INT NULL,
  Updated_At DATETIME NULL,
  PRIMARY KEY (Setting_Key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO APP_SETTINGS (Setting_Key, Setting_Value, Updated_At) VALUES ('idle_lock_minutes', '25', NOW());
