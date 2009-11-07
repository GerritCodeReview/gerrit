-- Upgrade: schema_version 6 to 7
--

ALTER TABLE account_project_watches ADD notify_submitted_changes CHAR(1) DEFAULT 'N' NOT NULL;

UPDATE account_project_watches SET notify_submitted_changes = 'Y'
WHERE (notify_new_changes = 'Y' OR notify_all_comments = 'Y')
AND EXISTS (SELECT 1
  FROM account_group_members m
   ,projects p
  WHERE
       m.account_id = account_project_watches.account_id
   AND m.group_id = p.owner_group_id
   AND p.project_id = account_project_watches.project_id);

CREATE INDEX account_project_watches_ntSub
ON account_project_watches (project_id)
WHERE notify_submitted_changes = 'Y';

CREATE INDEX starred_changes_byChange
ON starred_changes (change_id);

UPDATE schema_version SET version_nbr = 7;
