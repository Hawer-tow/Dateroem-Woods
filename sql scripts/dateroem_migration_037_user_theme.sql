-- Migration 037 (20 October 2026): each account's chosen colour theme.
--
-- "is it possible to add more themes like the dark and light theme and what
-- we have will be default ... a user can change the theme and it will be
-- remembered and when that user for that account goes into app or the mobile
-- the theme is still the one they choose last" - the website, the phone app
-- and the desktop app read it at sign-in and save it when it is changed.
--
-- USERS.Theme: 'forest' (the Living Forest greens, the default), 'dark' or
-- 'light'. A few bytes per account; nothing else is stored.

ALTER TABLE USERS ADD COLUMN Theme VARCHAR(10) NOT NULL DEFAULT 'forest' AFTER Can_Sync_Sms;
