-- Upgrade: schema_version 11 to 12 (part 2)
--

ALTER TABLE system_config DROP COLUMN contact_store_url;
ALTER TABLE system_config DROP COLUMN contact_store_appsec;
ALTER TABLE system_config DROP COLUMN gerrit_git_name;
ALTER TABLE system_config DROP COLUMN gerrit_git_email;
ALTER TABLE system_config DROP COLUMN sshd_port;
