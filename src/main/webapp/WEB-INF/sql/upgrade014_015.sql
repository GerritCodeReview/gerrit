-- Upgrade: schema_version 14 to 15
--

ALTER TABLE patches ADD reviewed_by VARCHAR;

UPDATE schema_version SET version_nbr = 15;
