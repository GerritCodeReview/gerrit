-- Upgrade: schema_version 16 to 17 (MySQL)
--

ALTER TABLE system_config DROP xsrf_private_key;
ALTER TABLE system_config CHANGE COLUMN account_private_key register_email_private_key VARCHAR(36) NOT NULL;

UPDATE schema_version SET version_nbr = 17;
