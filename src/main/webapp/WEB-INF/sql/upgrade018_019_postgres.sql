-- Upgrade: schema_version 18 to 19 (PostgreSQL)
--

BEGIN;

SELECT check_schema_version(18);

-- Per-project upload permission
INSERT INTO approval_category_values
(name, category_id, value)
VALUES
('Upload permission', 'READ', 2);

UPDATE project_rights SET max_value = 2
WHERE category_id = 'READ' AND max_value = 1;

ALTER TABLE account_groups ADD external_name VARCHAR(255);
ALTER TABLE account_groups ADD UNIQUE (external_name);

ALTER TABLE account_groups ADD group_type VARCHAR(8);

UPDATE account_groups SET group_type = 'SYSTEM'
WHERE group_id = (SELECT anonymous_group_id FROM system_config);

UPDATE account_groups SET group_type = 'SYSTEM'
WHERE group_id = (SELECT registered_group_id FROM system_config);

UPDATE account_groups SET group_type = 'LDAP'
WHERE automatic_membership = 'Y' AND group_type IS NULL;

UPDATE account_groups SET group_type = 'INTERNAL' WHERE group_type IS NULL;

ALTER TABLE account_groups ALTER group_type SET NOT NULL;
ALTER TABLE account_groups DROP automatic_membership;

DROP TABLE branches;

UPDATE schema_version SET version_nbr = 19;

COMMIT;
