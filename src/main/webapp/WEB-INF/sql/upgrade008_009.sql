-- Upgrade: schema_version 8 to 9
--

ALTER TABLE projects ADD submit_type CHAR(1);
UPDATE projects SET submit_type = 'M'; -- MERGE_IF_NECESSARY
ALTER TABLE projects ALTER COLUMN submit_type SET DEFAULT ' ';
ALTER TABLE projects ALTER COLUMN submit_type SET NOT NULL;

UPDATE schema_version SET version_nbr = 9;
