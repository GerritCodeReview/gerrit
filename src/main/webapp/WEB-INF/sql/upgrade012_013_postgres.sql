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

ALTER TABLE account_group_members_audit OWNER TO gerrit2;

UPDATE schema_version SET version_nbr = 13;
