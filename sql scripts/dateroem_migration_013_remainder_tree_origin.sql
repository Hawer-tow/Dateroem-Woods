-- "linking a specific trees (from a certain farmer) to the remainder
-- trees is better since one can use this to detect even the exact
-- location of the remainder trees by using the coordinates of the
-- originator tree" - remainders that get physically consolidated at one
-- storage household still trace back to which TREES row (farmer,
-- household, coordinates) they actually came from.
ALTER TABLE REMAINDER_TREES ADD COLUMN Origin_Trees_ID INT NULL AFTER Previous_Trip_ID;
ALTER TABLE REMAINDER_TREES ADD CONSTRAINT fk_remainder_trees_origin
    FOREIGN KEY (Origin_Trees_ID) REFERENCES TREES(Trees_ID);
