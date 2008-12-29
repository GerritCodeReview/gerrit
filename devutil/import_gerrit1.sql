-- PostgreSQL conversion from Gerrit 1 -> Gerrit 2
--
-- Execute this from your shell:
--
--  psql -c 'ALTER SCHEMA public RENAME TO gerrit1' $srcdb
--  pg_dump $srcdb | psql $dstdb
--  psql -f devutil/import_gerrit1.sql $dstdb
--
-- Run the ALTER commands displayed in a psql prompt.
--
-- Ensure the Git repositories are where git_base_path in the
-- system_config table says they should be.
--
-- Create a GerritServer.properties file for your database.
--
-- Run this from your shell:
--
--  make release
--  psql $dstdb -tAc 'select change_id,patch_set_id from patch_sets' \
--  | release/bin/gerrit2.sh \
--    --config=GerritServer.properties \
--    ReimportPatchSets
--

DELETE FROM accounts;
INSERT INTO accounts
(account_id,
 registered_on,
 full_name,
 preferred_email,
 contact_address,
 contact_country,
 contact_phone_nbr,
 contact_fax_nbr
) SELECT
 nextval('account_id'),
 a.created,
 a.real_name,
 a.user_email,
 a.mailing_address,
 a.mailing_address_country,
 a.phone_number,
 a.fax_number
 FROM gerrit1.accounts a;

DELETE FROM account_external_ids;
INSERT INTO account_external_ids
(account_id,
 external_id) SELECT
 l.account_id,
 'GoogleAccount/' || a.user_email
 FROM gerrit1.accounts a, accounts l
 WHERE l.preferred_email = a.user_email;

DELETE FROM contributor_agreements;
INSERT INTO contributor_agreements
(active,
 group_agreement,
 require_contact_information,
 short_name,
 short_description,
 agreement_html,
 id) VALUES (
 'Y',
 'N',
 'Y',
 'Individual',
 'If you are going to be contributing code on your own, this is the one you want. You can sign this one online.',
 'REPLACE ME',
 nextval('contributor_agreement_id'));
INSERT INTO contributor_agreements
(active,
 group_agreement,
 require_contact_information,
 short_name,
 short_description,
 agreement_html,
 id) VALUES (
 'Y',
 'Y',
 'N',
 'Corporate',
 'If you are going to be contributing code on behalf of your company, this is the one you want. We\'ll give you a form that will need to printed, signed and sent back via post, email or fax.',
 'REPLACE ME',
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

DELETE FROM account_group_members;
INSERT INTO account_group_members
(owner,
 account_id,
 group_id) SELECT
 'N',
 a.account_id,
 g.group_id
 FROM accounts a,
 account_groups g,
 gerrit1.account_group_users o
 WHERE
 o.group_name = g.name
 AND a.preferred_email = o.email;
UPDATE account_group_members SET owner = 'Y'
WHERE group_id = (SELECT group_id FROM account_groups
                  WHERE name = 'admin');

DELETE FROM projects;
INSERT INTO projects
(project_id,
 description,
 name) SELECT
 p.project_id,
 p.comment,
 p.name
 FROM gerrit1.projects p;

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

DELETE FROM project_lead_accounts;
INSERT INTO project_lead_accounts
(project_name,
 account_id) SELECT
 p.name,
 a.account_id
 FROM projects p,
 accounts a,
 gerrit1.project_owner_users o
 WHERE p.project_id = o.project_id
 AND a.preferred_email = o.email;

DELETE FROM project_lead_groups;
INSERT INTO project_lead_groups
(project_name,
 group_id) SELECT
 p.name,
 g.group_id
 FROM projects p,
 account_groups g,
 gerrit1.project_owner_groups o,
 gerrit1.account_groups og
 WHERE p.project_id = o.project_id
 AND og.gae_key = o.group_key
 AND g.name = og.name;

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
 CURRENT_TIMESTAMP,
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


-- Fix change.nbr_patch_sets
--
UPDATE changes
SET nbr_patch_sets = (SELECT MAX(p.patch_set_id)
                      FROM patch_sets p
                      WHERE p.change_id = changes.change_id);

-- Fix change.last_updated_on
--
DROP TABLE temp_dates;
CREATE TABLE temp_dates (
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
 (SELECT COUNT(*) FROM gerrit1.patches) as patches_g1,
 (SELECT COUNT(*) FROM patches) as patches_g2
WHERE
  (SELECT COUNT(*) FROM gerrit1.patches)
!=(SELECT COUNT(*) FROM patches);

SELECT
 (SELECT COUNT(*) FROM gerrit1.comments) as comments_g1,
 (SELECT COUNT(*) FROM patch_comments) as comments_g2
WHERE
  (SELECT COUNT(*) FROM gerrit1.comments)
!=(SELECT COUNT(*) FROM patch_comments);

--
SELECT 'ALTER SEQUENCE project_id RESTART WITH ' || (SELECT MAX(project_id) + 1 FROM projects) || ';' AS run_this;
SELECT 'ALTER SEQUENCE change_id RESTART WITH ' || (SELECT MAX(change_id) + 1 FROM changes) || ';' AS run_this;
