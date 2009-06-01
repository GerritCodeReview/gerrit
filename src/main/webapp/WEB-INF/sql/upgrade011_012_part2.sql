-- Upgrade: schema_version 11 to 12 (part 2)
--

ALTER TABLE system_config DROP COLUMN allow_google_account_upgrade;
ALTER TABLE system_config DROP COLUMN canonical_url;
ALTER TABLE system_config DROP COLUMN contact_store_url;
ALTER TABLE system_config DROP COLUMN contact_store_appsec;
ALTER TABLE system_config DROP COLUMN gerrit_git_name;
ALTER TABLE system_config DROP COLUMN gerrit_git_email;
ALTER TABLE system_config DROP COLUMN git_base_path;
ALTER TABLE system_config DROP COLUMN git_daemon_url;
ALTER TABLE system_config DROP COLUMN gitweb_url;
ALTER TABLE system_config DROP COLUMN email_format;
ALTER TABLE system_config DROP COLUMN login_type;
ALTER TABLE system_config DROP COLUMN login_http_header;
ALTER TABLE system_config DROP COLUMN sshd_port;
ALTER TABLE system_config DROP COLUMN use_contributor_agreements;
ALTER TABLE system_config DROP COLUMN use_repo_download;
