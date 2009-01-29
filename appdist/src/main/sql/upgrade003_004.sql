-- Upgrade: schema_version 3 to 4
--
ALTER TABLE system_config ADD use_repo_download CHAR(1);
UPDATE system_config SET use_repo_download = 'N';
ALTER TABLE system_config ALTER COLUMN use_repo_download SET DEFAULT 'N';
ALTER TABLE system_config ALTER COLUMN use_repo_download SET NOT NULL;

ALTER TABLE system_config ADD git_daemon_url VARCHAR(255);

UPDATE schema_version SET version_nbr = 4;
