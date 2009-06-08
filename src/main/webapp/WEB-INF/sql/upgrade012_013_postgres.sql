-- Upgrade: schema_version 12 to 13 (PostgreSQL)
--

CREATE TABLE account_group_members_audit
(account_id INT NOT NULL
,group_id INT NOT NULL
,added_on TIMESTAMP WITH TIME ZONE NOT NULL
,added_by INT NOT NULL
,removed_on TIMESTAMP WITH TIME ZONE
,removed_by INT
,PRIMARY KEY (account_id, group_id, added_on)
);

ALTER TABLE account_groups ADD automatic_membership CHAR(1);
UPDATE account_groups SET automatic_membership = 'N';
ALTER TABLE account_groups ALTER COLUMN automatic_membership SET DEFAULT 'N';
ALTER TABLE account_groups ALTER COLUMN automatic_membership SET NOT NULL;

UPDATE account_groups SET automatic_membership = 'Y'
WHERE group_id = (SELECT anonymous_group_id FROM system_config);

UPDATE account_groups SET automatic_membership = 'Y'
WHERE group_id = (SELECT registered_group_id FROM system_config);

ALTER TABLE account_group_members_audit OWNER TO gerrit2;

UPDATE schema_version SET version_nbr = 13;
