-- Upgrade: schema_version 9 to 10
--

ALTER TABLE patches DROP COLUMN content_SHA1;
DROP TABLE patch_contents;

UPDATE accounts SET full_name = NULL WHERE full_name = 'null null';

UPDATE schema_version SET version_nbr = 10;
