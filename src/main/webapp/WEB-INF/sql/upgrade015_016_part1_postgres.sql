-- Upgrade: schema_version 15 to 16 (PostgreSQL)
--

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


UPDATE schema_version SET version_nbr = 16;
