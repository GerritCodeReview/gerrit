-- Upgrade: schema_version 14 to 15
--
ALTER TABLE patch_comments ADD parent_uuid VARCHAR(40);

UPDATE schema_version SET version_nbr = 15;
