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

UPDATE schema_version SET version_nbr = 16;
