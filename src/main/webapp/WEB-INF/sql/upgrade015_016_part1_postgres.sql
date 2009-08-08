-- Upgrade: schema_version 15 to 16 (PostgreSQL)
--

-- Unset contributor agreement flag if site doesn't use them.
--
UPDATE projects SET use_contributor_agreements = 'N'
WHERE use_contributor_agreements = 'Y'
AND NOT EXISTS (SELECT 1 FROM contributor_agreements);


-- account_project_watches
--
DROP INDEX account_project_watches_ntcmt;
DROP INDEX account_project_watches_ntnew;
DROP INDEX account_project_watches_ntsub;

DELETE FROM account_project_watches WHERE project_id NOT IN (SELECT project_id FROM projects);
ALTER TABLE account_project_watches ADD project_name VARCHAR(255);
UPDATE account_project_watches SET project_name =
(SELECT name FROM projects
 WHERE account_project_watches.project_id = projects.project_id);
ALTER TABLE account_project_watches ALTER COLUMN project_name SET NOT NULL;
ALTER TABLE account_project_watches DROP CONSTRAINT account_project_watches_pkey;
ALTER TABLE account_project_watches ADD PRIMARY KEY (account_id, project_name);
ALTER TABLE account_project_watches DROP COLUMN project_id;

CREATE INDEX account_project_watches_ntNew
ON account_project_watches (project_name)
WHERE notify_new_changes = 'Y';

CREATE INDEX account_project_watches_ntCmt
ON account_project_watches (project_name)
WHERE notify_all_comments = 'Y';

CREATE INDEX account_project_watches_ntSub
ON account_project_watches (project_name)
WHERE notify_submitted_changes = 'Y';


-- project_rights
--
DELETE FROM project_rights WHERE project_id NOT IN (SELECT project_id FROM projects);
ALTER TABLE project_rights ADD project_name VARCHAR(255);
UPDATE project_rights SET project_name =
(SELECT name FROM projects
 WHERE project_rights.project_id = projects.project_id)
 WHERE project_id IS NOT NULL;
ALTER TABLE project_rights ALTER COLUMN project_name SET NOT NULL;
ALTER TABLE project_rights DROP CONSTRAINT project_rights_pkey;
ALTER TABLE project_rights ADD PRIMARY KEY (project_name, category_id, group_id);
ALTER TABLE project_rights DROP COLUMN project_id;


-- patch_set_approvals
--
ALTER TABLE change_approvals RENAME TO patch_set_approvals;
ALTER TABLE patch_set_approvals ADD patch_set_id INT;
UPDATE patch_set_approvals SET patch_set_id = (
  SELECT current_patch_set_id
  FROM changes
  WHERE changes.change_id = patch_set_approvals.change_id);
ALTER TABLE patch_set_approvals ALTER COLUMN patch_set_id SET NOT NULL;
ALTER TABLE patch_set_approvals DROP CONSTRAINT change_approvals_pkey;
ALTER TABLE patch_set_approvals DROP CONSTRAINT change_approvals_change_open_check;
ALTER TABLE patch_set_approvals ADD PRIMARY KEY (change_id, patch_set_id, account_id, category_id);
ALTER TABLE patch_set_approvals ADD CONSTRAINT patch_set_approvals_change_open_check CHECK (change_open IN ('Y', 'N'));
ALTER TABLE patch_set_approvals CLUSTER ON patch_set_approvals_pkey;

ALTER INDEX change_approvals_closedbyuser RENAME TO patch_set_approvals_closedbyuser;
ALTER INDEX change_approvals_openbyuser RENAME TO patch_set_approvals_openbyuser;


-- unique ssh_user_name
--
UPDATE accounts SET ssh_user_name = NULL
WHERE ssh_user_name IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM account_ssh_keys k
                WHERE k.account_id = accounts.account_id
                AND k.valid = 'Y');

UPDATE accounts SET ssh_user_name = NULL
WHERE ssh_user_name IS NOT NULL
AND (SELECT COUNT(*) FROM accounts b
     WHERE b.ssh_user_name = accounts.ssh_user_name) > 1
AND account_id <> (SELECT s.account_id FROM account_ssh_keys r, accounts s
                   WHERE s.ssh_user_name = accounts.ssh_user_name
                   AND r.account_id = s.account_id
                   AND r.last_used_on = 
                     (SELECT MAX(k.last_used_on)
                      FROM account_ssh_keys k, accounts b
                      WHERE b.ssh_user_name = accounts.ssh_user_name
                      AND k.account_id = b.account_id)
                   );

UPDATE accounts SET ssh_user_name = NULL
WHERE ssh_user_name IS NOT NULL
AND (SELECT COUNT(*) FROM accounts b
     WHERE b.ssh_user_name = accounts.ssh_user_name) > 1;

DROP INDEX accounts_bySshUserName;
CREATE UNIQUE INDEX accounts_ssh_user_name_key
ON accounts (ssh_user_name);


-- branch (no id)
--
ALTER TABLE branches DROP COLUMN branch_id;
DROP SEQUENCE branch_id;


UPDATE project_rights SET min_value=1
WHERE category_id='OWN' AND min_value=0 AND max_value=1;


UPDATE schema_version SET version_nbr = 16;
