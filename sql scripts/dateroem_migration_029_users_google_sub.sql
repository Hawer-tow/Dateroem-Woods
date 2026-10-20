-- "can my website have both the manual logging in and the google assisted
-- one" - Google_Sub is Google's own permanent id for the person (the "sub"
-- in their sign-in token), so a customer who signs in with Google finds the
-- same account every time even if they later change their Gmail address.
-- NULL for username/password accounts; such an account has no Password.
ALTER TABLE USERS
  ADD COLUMN Google_Sub VARCHAR(64) NULL AFTER Password,
  ADD UNIQUE KEY uq_users_google_sub (Google_Sub);
