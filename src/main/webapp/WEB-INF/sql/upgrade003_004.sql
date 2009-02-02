-- Upgrade: schema_version 3 to 4
--
ALTER TABLE system_config ADD use_repo_download CHAR(1);
UPDATE system_config SET use_repo_download = 'N';
ALTER TABLE system_config ALTER COLUMN use_repo_download SET DEFAULT 'N';
ALTER TABLE system_config ALTER COLUMN use_repo_download SET NOT NULL;

ALTER TABLE system_config ADD git_daemon_url VARCHAR(255);

ALTER TABLE accounts ADD show_site_header CHAR(1);
UPDATE accounts SET show_site_header = 'Y';
ALTER TABLE accounts ALTER COLUMN show_site_header SET DEFAULT 'N';
ALTER TABLE accounts ALTER COLUMN show_site_header SET NOT NULL;

UPDATE schema_version SET version_nbr = 4;

CREATE INDEX changes_byProjectOpen
ON changes (dest_project_name, sort_key)
WHERE open = 'Y';
