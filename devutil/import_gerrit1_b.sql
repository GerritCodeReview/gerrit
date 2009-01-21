-- Enable notifyNewChanges for project owners, approvers
-- Almost everyone has asked for this feature in Gerrit1,
-- so turn it on by default for people who are most likely
-- going to want it.
--
UPDATE account_project_watches
SET notify_new_changes = 'Y'
WHERE notify_new_changes = 'N'
AND EXISTS (SELECT 1
  FROM projects p,
    account_group_members g
  WHERE p.project_id = account_project_watches.project_id
    AND g.account_id = account_project_watches.account_id
    AND g.group_id = p.owner_group_id);

UPDATE account_project_watches
SET notify_new_changes = 'Y'
WHERE notify_new_changes = 'N'
AND EXISTS (SELECT 1
  FROM project_rights r,
    account_group_members g
  WHERE r.project_id = account_project_watches.project_id
    AND r.category_id = 'CRVW'
    AND r.max_value >= 2
    AND g.account_id = account_project_watches.account_id
    AND g.group_id = r.group_id);

