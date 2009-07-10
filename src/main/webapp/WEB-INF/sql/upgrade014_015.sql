-- Upgrade: schema_version 14 to 15
--

ALTER TABLE patches ADD reviewed SMALLINT DEFAULT 0;

UPDATE schema_version SET version_nbr = 15;
