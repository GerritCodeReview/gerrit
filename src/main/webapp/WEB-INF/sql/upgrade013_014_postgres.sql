-- Upgrade: schema_version 13 to 14 (PostgreSQL)
--

ALTER TABLE projects ADD use_signed_off_by CHAR(1);
UPDATE projects SET use_signed_off_by = 'N';
ALTER TABLE projects ALTER COLUMN use_signed_off_by SET DEFAULT 'N';
ALTER TABLE projects ALTER COLUMN use_signed_off_by SET NOT NULL;

UPDATE schema_version SET version_nbr = 14;
