-- Upgrade: schema_version 12 to 13 (MySQL)
--

CREATE TABLE account_group_members_audit
(account_id INT NOT NULL
,group_id INT NOT NULL
,added_on TIMESTAMP NOT NULL
,added_by INT NOT NULL
,removed_on TIMESTAMP
,removed_by INT
,PRIMARY KEY (account_id, group_id, added_on)
);

ALTER TABLE account_groups ADD automatic_membership CHAR(1);
UPDATE account_groups SET automatic_membership = 'N';
ALTER TABLE account_groups MODIFY automatic_membership CHAR(1) NOT NULL;

UPDATE account_groups SET automatic_membership = 'Y'
WHERE group_id = (SELECT anonymous_group_id FROM system_config);

UPDATE account_groups SET automatic_membership = 'Y'
WHERE group_id = (SELECT registered_group_id FROM system_config);

CREATE TABLE account_group_agreements
(accepted_on TIMESTAMP NOT NULL
,status CHAR(1) NOT NULL
,reviewed_by INT
,reviewed_on TIMESTAMP
,review_comments TEXT
,group_id INT NOT NULL
,cla_id INT NOT NULL
,PRIMARY KEY (group_id, cla_id)
);

UPDATE schema_version SET version_nbr = 13;
