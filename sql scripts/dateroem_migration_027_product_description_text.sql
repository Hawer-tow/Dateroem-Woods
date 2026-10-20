-- "i have the description of the product which is can be alot ... i want
-- to add the space in the db so that i can see more information in the
-- website" - VARCHAR(250) -> TEXT (up to 64KB). The backend caps what it
-- accepts at 10,000 characters so a stray paste gets a clear message
-- instead of a database error.
ALTER TABLE PRODUCTS MODIFY Description TEXT NULL;
