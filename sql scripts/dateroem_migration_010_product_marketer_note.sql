-- "one can fill a section product marketer that is apart from the
-- marketing style and one can fill e.g most ordered only 2 remaining and
-- this appears on the products in the product" - a short, admin/marketer
-- editable badge shown directly on each product card, replacing the
-- separate "Most Ordered" section that was showing the same product twice.
ALTER TABLE PRODUCTS ADD COLUMN Marketer_Note VARCHAR(120) NULL AFTER Attributes;
