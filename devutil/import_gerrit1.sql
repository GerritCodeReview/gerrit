-- PostgreSQL conversion from Gerrit 1 -> Gerrit 2
--
-- If this is the first time the Gerrit1 schema has been used it
-- needs to be renamed:
--     psql -c 'ALTER SCHEMA public RENAME TO gerrit1' $srcdb
--
-- Make sure there is a current dump file:
--     pg_dump -O -Fc $src >gerrit1.dump
--
-- Write your "UPDATE system_config SET ..." and put it into
-- some config.sql script.  At least git_base_path must be set.
--
-- Execute the conversion script:
--     devutil/1-to-2.sh gerrit1.dump config.sql
--

DELETE FROM accounts;
INSERT INTO accounts
(account_id,
 registered_on,
 full_name,
 preferred_email,
 ssh_user_name,
 contact_address,
 contact_country,
 contact_phone_nbr,
 contact_fax_nbr,
 default_context
) SELECT
 nextval('account_id'),
 a.created,
 a.real_name,
 a.user_email,
 CASE WHEN a.user_email LIKE '%@%'
      THEN lower(substring(a.user_email from '^(.*)@.*$'))
      ELSE NULL
 END,
 a.mailing_address,
 a.mailing_address_country,
 a.phone_number,
 a.fax_number,
 a.default_context
 FROM gerrit1.accounts a;

DELETE FROM account_external_ids;
INSERT INTO account_external_ids
(account_id,
 external_id,
 email_address) SELECT
 l.account_id,
 'https://www.google.com/accounts/o8/id?',
 a.user_email
 FROM gerrit1.accounts a, accounts l
 WHERE l.preferred_email = a.user_email;

DELETE FROM contributor_agreements;
INSERT INTO contributor_agreements
(active,
 require_contact_information,
 auto_verify,
 short_name,
 short_description,
 agreement_url,
 id) VALUES (
 'Y',
 'Y',
 'Y',
 'Individual',
 'If you are going to be contributing code on your own, this is the one you want. You can sign this one online.',
 'static/cla_individual.html',
 nextval('contributor_agreement_id'));
INSERT INTO contributor_agreements
(active,
 require_contact_information,
 auto_verify,
 short_name,
 short_description,
 agreement_url,
 id) VALUES (
 'Y',
 'N',
 'N',
 'Corporate',
 'If you are going to be contributing code on behalf of your company, this is the one you want. We\'ll give you a form that will need to printed, signed and sent back via post, email or fax.',
 'static/cla_corporate.html',
 nextval('contributor_agreement_id'));

DELETE FROM account_agreements;
INSERT INTO account_agreements
(accepted_on,
 status,
 account_id,
 review_comments,
 reviewed_by,
 reviewed_on,
 cla_id) SELECT
 a.individual_cla_timestamp,
 CASE WHEN a.cla_verified = 'Y' THEN 'V'
      ELSE 'n'
 END,
 r.account_id,
 a.cla_comments,
 (SELECT m.account_id FROM accounts m
  WHERE m.preferred_email = a.cla_verified_by),
 a.cla_verified_timestamp,
 i.id
 FROM contributor_agreements i,
 gerrit1.accounts a,
 accounts r
 WHERE i.short_name = 'Individual'
 AND a.individual_cla_version = 1
 AND r.preferred_email = a.user_email;
INSERT INTO account_agreements
(accepted_on,
 status,
 account_id,
 review_comments,
 reviewed_by,
 reviewed_on,
 cla_id) SELECT
 CASE WHEN a.individual_cla_timestamp IS NOT NULL
      THEN a.individual_cla_timestamp
      ELSE a.created
      END,
 'V',
 r.account_id,
 a.cla_comments,
 (SELECT m.account_id FROM accounts m
  WHERE m.preferred_email = a.cla_verified_by),
 a.cla_verified_timestamp,
 i.id
 FROM contributor_agreements i,
 gerrit1.accounts a,
 accounts r
 WHERE i.short_name = 'Corporate'
 AND a.individual_cla_version = 0
 AND a.cla_verified = 'Y'
 AND r.preferred_email = a.user_email;

DELETE FROM account_groups;
INSERT INTO account_groups
(group_id,
 description,
 name) SELECT
 nextval('account_group_id'),
 g.comment,
 g.name
 FROM gerrit1.account_groups g;

INSERT INTO account_groups
(group_id,
 description,
 name) VALUES
(nextval('account_group_id'),
'Any user, signed-in or not',
'Anonymous Users');

INSERT INTO account_groups
(group_id,
 description,
 name) VALUES
(nextval('account_group_id'),
'Any signed-in user',
'Registered Users');


DELETE FROM account_group_members;
INSERT INTO account_group_members
(account_id,
 group_id) SELECT
 a.account_id,
 g.group_id
 FROM accounts a,
 account_groups g,
 gerrit1.account_group_users o
 WHERE
 o.group_name = g.name
 AND a.preferred_email = o.email;

UPDATE system_config
SET
 use_contributor_agreements = 'Y'
,admin_group_id = (SELECT group_id
                  FROM account_groups
                  WHERE name = 'admin')
,anonymous_group_id = (SELECT group_id
                      FROM account_groups
                      WHERE name = 'Anonymous Users')
,registered_group_id = (SELECT group_id
                      FROM account_groups
                      WHERE name = 'Registered Users');

UPDATE account_groups
SET owner_group_id = (SELECT admin_group_id FROM system_config);

DELETE FROM projects;
INSERT INTO projects
(project_id,
 description,
 name,
 use_contributor_agreements,
 owner_group_id) SELECT
 p.project_id,
 p.comment,
 p.name,
 'Y',
 (SELECT admin_group_id FROM system_config)
 FROM gerrit1.projects p;

DELETE FROM account_project_watches;
INSERT INTO account_project_watches
(account_id,
 project_id) SELECT
 a.account_id,
 p.project_id
 FROM gerrit1.projects p,
 accounts a,
 gerrit1.account_unclaimed_changes_projects q
 WHERE a.preferred_email = q.email
 AND p.gae_key = q.project_key;

DELETE FROM branches;
INSERT INTO branches
(branch_id,
 project_name,
 branch_name) SELECT
 nextval('branch_id'),
 p.name,
 b.name
 FROM gerrit1.branches b, gerrit1.projects p
 WHERE p.gae_key = b.project_key;

CREATE TEMPORARY TABLE need_groups (project_id INT NOT NULL);
INSERT INTO need_groups
 SELECT p.project_id
 FROM projects p, gerrit1.project_owner_groups o
 WHERE p.project_id = o.project_id
 GROUP BY p.project_id
 HAVING COUNT(*) > 1;
INSERT INTO need_groups
 SELECT p.project_id
 FROM projects p
 WHERE EXISTS (SELECT 1 FROM gerrit1.project_owner_users u
               WHERE u.project_id = p.project_id)
 AND NOT EXISTS (SELECT 1 FROM need_groups n
                 WHERE n.project_id = p.project_id);

INSERT INTO account_groups
(group_id,
 owner_group_id,
 description,
 name) SELECT
 nextval('account_group_id'),
 (SELECT admin_group_id FROM system_config),
 p.name || ' maintainers',
 substring(p.name from '^.*/([^/]*)$') || '_' || p.project_id || '-owners'
 FROM projects p, need_groups g
 WHERE p.project_id = g.project_id;

UPDATE account_groups
SET owner_group_id = group_id
WHERE name IN (SELECT
 substring(p.name from '^.*/([^/]*)$') || '_' || p.project_id || '-owners'
 FROM projects p, need_groups g
 WHERE p.project_id = g.project_id);

UPDATE projects
SET owner_group_id = (SELECT group_id
FROM account_groups
WHERE name = substring(projects.name
from '^.*/([^/]*)$') || '_' || projects.project_id || '-owners')
WHERE project_id IN (SELECT project_id FROM need_groups);

INSERT INTO account_group_members
(account_id,
 group_id) SELECT DISTINCT
 q.account_id,
 p.owner_group_id
 FROM projects p,
 need_groups n,
 gerrit1.account_groups og,
 gerrit1.project_owner_groups o,
 account_groups g,
 account_group_members q
 WHERE
 n.project_id = p.project_id
 AND o.project_id = p.project_id
 AND og.gae_key = o.group_key
 AND g.name = og.name
 AND q.group_id = g.group_id
 UNION
 SELECT
 a.account_id,
 p.owner_group_id
 FROM accounts a,
 projects p,
 need_groups n,
 gerrit1.project_owner_users o
 WHERE
 n.project_id = p.project_id
 AND o.project_id = p.project_id
 AND a.preferred_email = o.email;

UPDATE projects
SET owner_group_id = (
 SELECT g.group_id
 FROM account_groups g,
 gerrit1.project_owner_groups o,
 gerrit1.account_groups og
 WHERE projects.project_id = o.project_id
 AND og.gae_key = o.group_key
 AND g.name = og.name)
WHERE project_id NOT IN (SELECT project_id FROM need_groups)
AND EXISTS (
 SELECT g.group_id
 FROM account_groups g,
 gerrit1.project_owner_groups o,
 gerrit1.account_groups og
 WHERE projects.project_id = o.project_id
 AND og.gae_key = o.group_key
 AND g.name = og.name);

DROP TABLE need_groups;
 

DELETE FROM changes;
INSERT INTO changes
(created_on,
 last_updated_on,
 owner_account_id,
 dest_project_name,
 dest_branch_name,
 status,
 nbr_patch_sets,
 current_patch_set_id,
 subject,
 change_id) SELECT
 c.created,
 c.modified,
 a.account_id,
 p.name,
 b.name,
 CASE WHEN c.merged = 'Y' THEN 'M'
      WHEN c.closed = 'Y' THEN 'A'
      ELSE 'n'
 END,
 c.n_patchsets,
 c.n_patchsets,
 c.subject,
 c.change_id
 FROM gerrit1.changes c,
 accounts a,
 gerrit1.projects p,
 gerrit1.branches b
 WHERE 
 a.preferred_email = c.owner
 AND p.gae_key = c.dest_project_key
 AND b.gae_key = c.dest_branch_key
 ;

UPDATE gerrit1.messages
SET sender = substring(sender from '<(.*)>')
WHERE sender LIKE '%<%>';

UPDATE gerrit1.messages
SET sender = NULL
WHERE sender = 'code-review@android.com';

UPDATE gerrit1.messages
SET body = 'Change has been successfully merged into the git repository.'
WHERE body LIKE '
Hi.

Your change has been successfully merged into the git repository.

-Your friendly git merger%'
AND sender IS NULL;

UPDATE gerrit1.messages
SET body = 'Change could not be merged because of a missing dependency.  As
soon as its dependencies are submitted, the change will be submitted.'
WHERE body LIKE '
Hi.

Your change could not be merged because of a missing dependency.%

-Your friendly git merger%'
AND sender IS NULL;

UPDATE gerrit1.messages
SET body = 'Change cannot be merged because of a path conflict.'
WHERE body LIKE '
Hi.

Your change has not been successfully merged into the git repository
because of a path conflict.

-Your friendly git merger%'
AND sender is NULL;


DELETE FROM change_messages;
INSERT INTO change_messages
(change_id,
 uuid,
 author_id,
 written_on,
 message) SELECT
 c.change_id,
 substr(m.gae_key, length(m.change_key) + length('wLEgdNZXNzYWdlG')),
 a.account_id,
 m.date_sent,
 m.body
 FROM gerrit1.messages m
 LEFT OUTER JOIN accounts a ON a.preferred_email = m.sender,
 gerrit1.changes c
 WHERE
 c.gae_key = m.change_key;

DELETE FROM patch_sets;
INSERT INTO patch_sets
(revision,
 change_id,
 patch_set_id) SELECT
 r.revision_id,
 c.change_id,
 p.patchset_id
 FROM gerrit1.patch_sets p
 JOIN gerrit1.changes c ON p.change_key = c.gae_key
 LEFT OUTER JOIN gerrit1.revisions r ON r.gae_key = p.revision_key;

DELETE FROM patch_set_info;
INSERT INTO patch_set_info
(subject,
 message,
 author_name,
 author_email,
 author_when,
 author_tz,
 committer_name,
 committer_email,
 committer_when,
 committer_tz,
 change_id,
 patch_set_id) SELECT DISTINCT
 (SELECT c.subject FROM changes c
  WHERE c.change_id = p.change_id
  AND c.current_patch_set_id = p.patch_set_id),
 r.message,
 r.author_name,
 r.author_email,
 r.author_when,
 r.author_tz,
 r.committer_name,
 r.committer_email,
 r.committer_when,
 r.committer_tz,
 p.change_id,
 p.patch_set_id
 FROM gerrit1.revisions r, patch_sets p
 WHERE r.revision_id = p.revision;

DELETE FROM patch_set_ancestors;
INSERT INTO patch_set_ancestors
(ancestor_revision,
change_id,
patch_set_id,
position
) SELECT DISTINCT
 p.parent_id,
 ps.change_id,
 ps.patch_set_id,
 p.position
 FROM gerrit1.revision_ancestors p,
 patch_sets ps
 WHERE ps.revision = p.child_id;

DELETE FROM patches;
INSERT INTO patches
(change_type,
 patch_type,
 nbr_comments,
 change_id,
 patch_set_id,
 file_name) SELECT
 p.status,
 CASE WHEN p.multi_way_diff = 'Y' THEN 'N'
      ELSE 'U'
 END,
 p.n_comments,
 c.change_id,
 ps.patchset_id,
 p.filename
 FROM gerrit1.patches p,
 gerrit1.patch_sets ps,
 gerrit1.changes c
 WHERE p.patchset_key = ps.gae_key
 AND ps.change_key = c.gae_key;

DELETE FROM patch_comments;
INSERT INTO patch_comments
(line_nbr,
 author_id,
 written_on,
 status,
 side,
 message,
 change_id,
 patch_set_id,
 file_name,
 uuid) SELECT
 c.lineno,
 a.account_id,
 c.written,
 CASE WHEN c.draft = 'Y' THEN 'd'
      ELSE 'P'
 END,
 CASE WHEN c.is_left = 'Y' THEN 0
      ELSE 1
 END,
 c.body,
 o_c.change_id,
 o_ps.patchset_id,
 o_p.filename,
 c.message_id
 FROM gerrit1.comments c,
 accounts a,
 gerrit1.patches o_p,
 gerrit1.patch_sets o_ps,
 gerrit1.changes o_c
 WHERE o_p.patchset_key = o_ps.gae_key
 AND o_ps.change_key = o_c.gae_key
 AND o_p.gae_key = c.patch_key
 AND a.preferred_email = c.author;

DELETE FROM change_approvals;
INSERT INTO change_approvals
(value,
 change_id,
 account_id,
 category_id) SELECT
 1,
 c.change_id,
 a.account_id,
 'VRIF'
 FROM gerrit1.review_status s,
 gerrit1.changes c,
 accounts a
 WHERE
 s.verified = 'Y'
 AND s.change_key = c.gae_key
 AND a.preferred_email = s.email;
INSERT INTO change_approvals
(value,
 change_id,
 account_id,
 category_id) SELECT
 CASE WHEN s.lgtm = 'lgtm' THEN 2
      WHEN s.lgtm = 'yes' THEN 1
      WHEN s.lgtm = 'abstain' THEN 0
      WHEN s.lgtm = 'no' THEN -1
      WHEN s.lgtm = 'reject' THEN -2
      ELSE NULL
 END,
 c.change_id,
 a.account_id,
 'CRVW'
 FROM gerrit1.review_status s,
 gerrit1.changes c,
 accounts a
 WHERE
 s.lgtm IS NOT NULL
 AND s.change_key = c.gae_key
 AND a.preferred_email = s.email;

DELETE FROM starred_changes;
INSERT INTO starred_changes
(account_id,
 change_id) SELECT
 a.account_id,
 c.change_id
FROM gerrit1.account_stars s,
     accounts a,
     changes c
WHERE a.preferred_email = s.email
      AND c.change_id = s.change_id;

UPDATE account_groups
SET name = 'Administrators'
WHERE name = 'admin';

-- Fix change.nbr_patch_sets
--
UPDATE changes
SET nbr_patch_sets = (SELECT MAX(p.patch_set_id)
                      FROM patch_sets p
                      WHERE p.change_id = changes.change_id);

-- Fix change.last_updated_on
--
CREATE TEMPORARY TABLE temp_dates (
change_id INT NOT NULL,
dt TIMESTAMP NOT NULL);

INSERT INTO temp_dates
SELECT change_id,written_on
FROM patch_comments
WHERE status = 'P';

INSERT INTO temp_dates
SELECT change_id,written_on FROM change_messages;

INSERT INTO temp_dates
SELECT change_id,merge_submitted
FROM gerrit1.changes
WHERE merge_submitted IS NOT NULL
AND merged = 'Y';

UPDATE changes
SET last_updated_on = (SELECT MAX(m.dt)
                       FROM temp_dates m
                       WHERE m.change_id = changes.change_id)
WHERE EXISTS (SELECT 1 FROM temp_dates m
              WHERE m.change_id = changes.change_id);
DROP TABLE temp_dates;


-- Fix patches.nbr_comments
--
UPDATE patches
SET nbr_comments = (SELECT COUNT(*)
                    FROM patch_comments c
                    WHERE c.status = 'P'
                    AND c.change_id = patches.change_id
                    AND c.patch_set_id = patches.patch_set_id
                    AND c.file_name = patches.file_name);

SELECT
 (SELECT COUNT(*) FROM gerrit1.accounts) as accounts_g1,
 (SELECT COUNT(*) FROM accounts) as accounts_g1
WHERE
  (SELECT COUNT(*) FROM gerrit1.accounts)
!=(SELECT COUNT(*) FROM accounts);

SELECT
 (SELECT COUNT(*) FROM gerrit1.changes) as changes_g1,
 (SELECT COUNT(*) FROM changes) as changes_g2
WHERE 
  (SELECT COUNT(*) FROM gerrit1.changes)
!=(SELECT COUNT(*) FROM changes);

SELECT
 (SELECT COUNT(*) FROM gerrit1.messages) as messages_g1,
 (SELECT COUNT(*) FROM change_messages) as messages_g2
WHERE 
  (SELECT COUNT(*) FROM gerrit1.messages)
!=(SELECT COUNT(*) FROM change_messages);

SELECT
 (SELECT COUNT(*) FROM gerrit1.patch_sets) as patch_sets_g1,
 (SELECT COUNT(*) FROM patch_sets) as patch_sets_g2
WHERE
  (SELECT COUNT(*) FROM gerrit1.patch_sets)
!=(SELECT COUNT(*) FROM patch_sets);

SELECT
 (SELECT COUNT(*) FROM gerrit1.patches g, gerrit1.patch_sets p
  WHERE g.patchset_key = p.gae_key) as patches_g1,
 (SELECT COUNT(*) FROM patches) as patches_g2
WHERE
  (SELECT COUNT(*) FROM gerrit1.patches g, gerrit1.patch_sets p
   WHERE g.patchset_key = p.gae_key)
!=(SELECT COUNT(*) FROM patches);

SELECT
 (SELECT COUNT(*) FROM gerrit1.comments) as comments_g1,
 (SELECT COUNT(*) FROM patch_comments) as comments_g2
WHERE
  (SELECT COUNT(*) FROM gerrit1.comments)
!=(SELECT COUNT(*) FROM patch_comments);

-- Reset sequences
--
-- account_group_id (above)
-- account_id (above)
-- branch_id (above)
SELECT setval('change_id',(SELECT MAX(change_id) FROM changes));
-- contributor_agreement_id (above)
SELECT setval('project_id',(SELECT MAX(project_id) FROM projects));

-- Grant access to read tables needed for import
--
GRANT SELECT ON gerrit1.project_code_reviews TO gerrit2;
GRANT SELECT ON gerrit1.approval_right_groups TO gerrit2;
GRANT SELECT ON gerrit1.approval_right_users TO gerrit2;
GRANT SELECT ON gerrit1.approval_rights TO gerrit2;
GRANT SELECT ON gerrit1.account_groups TO gerrit2;
