-- Upgrade: schema_version 13 to 14
--

CREATE INDEX changed_approvals_byAccountId
ON change_approvals (account_id);

UPDATE schema_version SET version_nbr = 14;
