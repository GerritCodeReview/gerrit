-- Upgrade: schema_version 17 to 18 (PostgreSQL)
--

BEGIN;

SELECT check_schema_version(17);

ALTER TABLE approval_categories ADD abbreviated_name VARCHAR(4);
UPDATE approval_categories SET abbreviated_name = 'V' WHERE category_id = 'VRIF';
UPDATE approval_categories SET abbreviated_name = 'R' WHERE category_id = 'CRVW';

DROP TABLE patches;

UPDATE schema_version SET version_nbr = 18;

COMMIT;
