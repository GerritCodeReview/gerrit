-- Upgrade: schema_version 8 to 9
--

ALTER TABLE projects ADD submit_type CHAR(1);
UPDATE projects SET submit_type = 'M'; -- MERGE_IF_NECESSARY
ALTER TABLE projects ALTER COLUMN submit_type SET DEFAULT ' ';
ALTER TABLE projects ALTER COLUMN submit_type SET NOT NULL;

UPDATE change_messages
SET author_id = (SELECT a.account_id FROM change_approvals a
                 WHERE a.change_id = change_messages.change_id
                   AND a.category_id = 'SUBM'
                   AND a.value = 1)
WHERE author_id IS NULL
 AND message LIKE '% has been successfully merged %'
AND 'M' = (SELECT c.status FROM changes c
           WHERE c.change_id = change_messages.change_id)
AND 1 = (SELECT COUNT(a.account_id) FROM change_approvals a
         WHERE a.change_id = change_messages.change_id
           AND a.category_id = 'SUBM'
           AND a.value = 1)
AND written_on >= (SELECT MAX(a.granted)
                   FROM change_approvals a
                   WHERE a.change_id = change_messages.change_id
                     AND a.category_id = 'SUBM'
                     AND a.value = 1);

UPDATE schema_version SET version_nbr = 9;
