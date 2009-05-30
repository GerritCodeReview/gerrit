-- Upgrade: schema_version 11 to 12
--

ALTER TABLE system_config DROP COLUMN sshd_port;

UPDATE schema_version SET version_nbr = 12;
