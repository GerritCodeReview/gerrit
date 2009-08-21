-- Upgrade: schema_version 16 to 17 (MySQL)
--

ALTER TABLE system_config DROP xsrf_private_key;
ALTER TABLE system_config DROP max_session_age;
ALTER TABLE system_config CHANGE COLUMN account_private_key register_email_private_key VARCHAR(36) NOT NULL;
ALTER TABLE changes ADD change_key VARCHAR(60);

UPDATE changes SET change_key = 'I' || (SELECT p.revision
  FROM patch_sets p
  WHERE p.change_id = changes.change_id
  AND p.patch_set_id = 1);

CREATE INDEX changes_key
ON changes (change_key);

ALTER TABLE changes MODIFY change_key VARCHAR(60) NOT NULL;

UPDATE schema_version SET version_nbr = 17;
