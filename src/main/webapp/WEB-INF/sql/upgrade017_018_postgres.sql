-- Upgrade: schema_version 17 to 18 (PostgreSQL)
--

BEGIN;

SELECT check_schema_version(17);

DROP TABLE patches;

UPDATE schema_version SET version_nbr = 18;

COMMIT;
