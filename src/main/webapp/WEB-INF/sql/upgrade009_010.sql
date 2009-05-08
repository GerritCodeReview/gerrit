-- Upgrade: schema_version 9 to 10
--

ALTER TABLE patches DROP COLUMN content_SHA1;
DROP TABLE patch_contents;

UPDATE schema_version SET version_nbr = 10;
