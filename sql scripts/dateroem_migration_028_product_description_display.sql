-- "i may have pasted a piece of data but the system can check and provide
-- better arrangement for the user ... if one did not provide the proper
-- arrangement due to a hurry" - the AI-tidied copy of a long description
-- that customers see (DescriptionFormatter). Description itself stays
-- exactly as the seller typed it, so the edit forms never show a rewrite.
-- Description_Display_Source is SHA2(Description) at the time it was made:
-- when the two differ the copy is stale and gets redone.
ALTER TABLE PRODUCTS
  ADD COLUMN Description_Display TEXT NULL AFTER Description,
  ADD COLUMN Description_Display_Source CHAR(64) NULL AFTER Description_Display;
