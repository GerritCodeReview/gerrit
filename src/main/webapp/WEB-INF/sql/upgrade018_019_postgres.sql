-- Upgrade: schema_version 18 to 19 (PostgreSQL)
--

BEGIN;

SELECT check_schema_version(18);

-- Per-project upload permission
INSERT INTO approval_category_values
(name, category_id, value)
VALUES
('Upload permission', 'READ', 2);

UPDATE project_rights SET max_value = 2
WHERE category_id = 'READ' AND max_value = 1;

UPDATE schema_version SET version_nbr = 18;

COMMIT;
