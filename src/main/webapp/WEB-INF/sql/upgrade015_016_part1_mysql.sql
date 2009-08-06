-- Upgrade: schema_version 15 to 16 (MySQL)
--

-- account_project_watches
--
DELETE FROM account_project_watches WHERE project_id NOT IN (SELECT project_id FROM projects);
ALTER TABLE account_project_watches ADD project_name VARCHAR(255);
UPDATE account_project_watches SET project_name =
(SELECT name FROM projects
 WHERE account_project_watches.project_id = projects.project_id);
ALTER TABLE account_project_watches MODIFY COLUMN project_name VARCHAR(255) NOT NULL;
ALTER TABLE account_project_watches DROP PRIMARY KEY;
ALTER TABLE account_project_watches ADD PRIMARY KEY (account_id, project_name);
ALTER TABLE account_project_watches DROP COLUMN project_id;

DROP INDEX account_project_watches_ntcmt ON account_project_watches;
DROP INDEX account_project_watches_ntnew ON account_project_watches;
DROP INDEX account_project_watches_ntsub ON account_project_watches;

CREATE INDEX account_project_watches_ntNew
ON account_project_watches (notify_new_changes, project_name);

CREATE INDEX account_project_watches_ntCmt
ON account_project_watches (notify_all_comments, project_name);

CREATE INDEX account_project_watches_ntSub
ON account_project_watches (notify_submitted_changes, project_name);


-- project_rights
--
DELETE FROM project_rights WHERE project_id NOT IN (SELECT project_id FROM projects);
ALTER TABLE project_rights ADD project_name VARCHAR(255);
UPDATE project_rights SET project_name =
(SELECT name FROM projects
 WHERE project_rights.project_id = projects.project_id);
ALTER TABLE project_rights MODIFY COLUMN project_name VARCHAR(255) NOT NULL;
ALTER TABLE project_rights DROP PRIMARY KEY;
ALTER TABLE project_rights ADD PRIMARY KEY (project_name, category_id, group_id);
ALTER TABLE project_rights DROP COLUMN project_id;


-- patch_set_approvals
--
RENAME TABLE change_approvals TO patch_set_approvals;
ALTER TABLE patch_set_approvals ADD patch_set_id INT;
UPDATE patch_set_approvals SET patch_set_id = (
  SELECT current_patch_set_id
  FROM changes
  WHERE changes.change_id = patch_set_approvals.change_id);
ALTER TABLE patch_set_approvals MODIFY COLUMN patch_set_id INT NOT NULL;
ALTER TABLE patch_set_approvals DROP PRIMARY KEY;
ALTER TABLE patch_set_approvals ADD PRIMARY KEY (change_id, patch_set_id, account_id, category_id);

ALTER INDEX change_approvals_closedbyuser RENAME TO patch_set_approvals_closedbyuser;
ALTER INDEX change_approvals_openbyuser RENAME TO patch_set_approvals_openbyuser;


-- unique ssh_user_name
--
UPDATE accounts SET ssh_user_name = NULL
WHERE ssh_user_name IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM account_ssh_keys k
                WHERE k.account_id = accounts.account_id
                AND k.valid = 'Y');

DROP INDEX accounts_bySshUserName ON accounts;
CREATE UNIQUE INDEX accounts_ssh_user_name_key
ON accounts (ssh_user_name);


UPDATE schema_version SET version_nbr = 16;
