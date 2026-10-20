-- "if a remainder tree is claimed then dont let it to be claimed by
-- another tree creation" - a remainder can be carried forward by at most
-- one TREES row. NULL is unaffected (MySQL allows many NULLs under a
-- unique index), so trees not carrying a remainder forward are untouched.
ALTER TABLE TREES ADD CONSTRAINT uq_trees_remainder_trees_id UNIQUE (Remainder_Trees_ID);
