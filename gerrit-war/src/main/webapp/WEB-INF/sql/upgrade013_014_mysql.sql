-- Upgrade: schema_version 13 to 14 (MySQL)
--

ALTER TABLE projects ADD use_signed_off_by CHAR(1);
UPDATE projects SET use_signed_off_by = 'N';
ALTER TABLE projects MODIFY use_signed_off_by CHAR(1) DEFAULT 'N' NOT NULL;

UPDATE schema_version SET version_nbr = 14;
