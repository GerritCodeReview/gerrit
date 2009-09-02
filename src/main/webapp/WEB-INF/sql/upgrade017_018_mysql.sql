-- Upgrade: schema_version 17 to 18 (MySQL)
--

DROP TABLE patches;

UPDATE schema_version SET version_nbr = 18;
