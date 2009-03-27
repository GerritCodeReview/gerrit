-- Upgrade: schema_version 7 to 8
--

UPDATE approval_category_values
SET name = 'Create Signed Tag'
WHERE category_id = 'pTAG' AND value = 1;

INSERT INTO approval_category_values
 (category_id, value, name)
VALUES
 ('pTAG', 2, 'Create Annotated Tag');

INSERT INTO approval_category_values
 (category_id, value, name)
VALUES
 ('pTAG', 3, 'Create Any Tag');

UPDATE project_rights
SET max_value = 2
WHERE max_value = 1
  AND category_id = 'pTAG'; 

UPDATE schema_version SET version_nbr = 8;
