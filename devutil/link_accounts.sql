-- Transfers all account data from one account to another.
--
-- Create /tmp/link_accounts.csv as "junk,from_email,to_email"
-- chmod a+r /tmp/link_accounts.csv
-- (must be on the PostgreSQL server)

DROP TABLE links;
DROP TABLE to_del;

CREATE TEMP TABLE links
(date_junk VARCHAR(255),
 from_email VARCHAR(255),
 to_email VARCHAR(255));

COPY links FROM '/tmp/link_accounts.csv' DELIMITER ',';
ALTER TABLE links DROP date_junk;

ALTER TABLE links ADD from_id INT;
ALTER TABLE links ADD to_id INT;

UPDATE links
SET from_id = (SELECT account_id FROM accounts
               WHERE preferred_email = links.from_email)
,   to_id   = (SELECT account_id FROM accounts
               WHERE preferred_email = links.to_email);
DELETE FROM links
WHERE (from_email = to_email)
   OR (from_id IS NULL AND to_id IS NULL);

CREATE TEMP TABLE to_del (old_id INT);

BEGIN TRANSACTION;

INSERT INTO account_external_ids
(email_address, account_id, external_id)
SELECT
 l.from_email
,l.to_id
,'Google Account ' || l.from_email
FROM links l
WHERE l.to_id IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM account_external_ids e
  WHERE e.email_address = l.from_email
  AND e.account_id = l.to_id
  AND e.external_id = 'Google Account ' || l.from_email);

INSERT INTO account_external_ids
(email_address, account_id, external_id)
SELECT
 l.to_email
,l.from_id
,'Google Account ' || l.to_email
FROM links l
WHERE l.from_id IS NOT NULL AND l.to_id IS NULL
AND NOT EXISTS (SELECT 1 FROM account_external_ids e
  WHERE e.email_address = l.to_email
  AND e.account_id = l.from_id
  AND e.external_id = 'Google Account ' || l.to_email);

INSERT INTO starred_changes
(account_id, change_id)
SELECT l.to_id, s.change_id
FROM links l, starred_changes s
WHERE l.from_id IS NOT NULL
  AND l.to_id IS NOT NULL
  AND s.account_id = l.from_id
  AND NOT EXISTS (SELECT 1 FROM starred_changes e
                  WHERE e.account_id = l.to_id
                  AND e.change_id = s.change_id);

INSERT INTO account_project_watches
(account_id, project_id)
SELECT l.to_id, s.project_id
FROM links l, account_project_watches s
WHERE l.from_id IS NOT NULL
  AND l.to_id IS NOT NULL
  AND s.account_id = l.from_id
  AND NOT EXISTS (SELECT 1 FROM account_project_watches e
                  WHERE e.account_id = l.to_id
                  AND e.project_id = s.project_id);

INSERT INTO account_group_members
(account_id, group_id)
SELECT l.to_id, s.group_id
FROM links l, account_group_members s
WHERE l.from_id IS NOT NULL
  AND l.to_id IS NOT NULL
  AND s.account_id = l.from_id
  AND NOT EXISTS (SELECT 1 FROM account_group_members e
                  WHERE e.account_id = l.to_id
                  AND e.group_id = s.group_id);

UPDATE changes
SET owner_account_id = (SELECT l.to_id
                        FROM links l
                        WHERE l.from_id = owner_account_id)
WHERE EXISTS (SELECT 1 FROM links l
              WHERE l.to_id IS NOT NULL
                AND l.from_id IS NOT NULL
                AND l.from_id = owner_account_id);

UPDATE change_approvals
SET account_id = (SELECT l.to_id
                  FROM links l
                  WHERE l.from_id = account_id)
WHERE EXISTS (SELECT 1 FROM links l
              WHERE l.to_id IS NOT NULL
                AND l.from_id IS NOT NULL
                AND l.from_id = account_id)
 AND NOT EXISTS (SELECT 1 FROM change_approvals e, links l
                 WHERE e.change_id = change_approvals.change_id
                   AND e.account_id = l.to_id
                   AND e.category_id = change_approvals.category_id
                   AND l.from_id = change_approvals.account_id);

UPDATE change_messages
SET author_id = (SELECT l.to_id
                 FROM links l
                 WHERE l.from_id = author_id)
WHERE EXISTS (SELECT 1 FROM links l
              WHERE l.to_id IS NOT NULL
                AND l.from_id IS NOT NULL
                AND l.from_id = author_id);

UPDATE patch_comments
SET author_id = (SELECT l.to_id
                 FROM links l
                 WHERE l.from_id = author_id)
WHERE EXISTS (SELECT 1 FROM links l
              WHERE l.to_id IS NOT NULL
                AND l.from_id IS NOT NULL
                AND l.from_id = author_id);


-- Destroy the from account
--
INSERT INTO to_del
SELECT from_id FROM links
WHERE to_id IS NOT NULL
AND from_id IS NOT NULL;

DELETE FROM account_agreements WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM account_external_ids WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM account_group_members WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM account_project_watches WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM account_ssh_keys WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM accounts WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM starred_changes WHERE account_id IN (SELECT old_id FROM to_del);
DELETE FROM change_approvals WHERE account_id IN (SELECT old_id FROM to_del);

COMMIT;
