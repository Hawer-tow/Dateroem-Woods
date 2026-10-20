-- Products can be stocked either as a discrete count ("pieces") or as a
-- volume measurement ("board feet") - business ask: "total body feet if
-- the product is measured in volume rather than stock (numbers)". Rather
-- than add a second quantity column, Stock's existing decimal(10,2) holds
-- whichever number applies, and this column records how to read it.
ALTER TABLE PRODUCTS ADD COLUMN Unit VARCHAR(20) NOT NULL DEFAULT 'pieces' AFTER Stock;
