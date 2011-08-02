-- Gerrit 2 : PostgreSQL
--

-- Cluster hot tables by their primary method of access
--
ALTER TABLE patch_sets CLUSTER ON patch_sets_pkey;
ALTER TABLE change_messages CLUSTER ON change_messages_pkey;
ALTER TABLE patch_comments CLUSTER ON patch_comments_pkey;
ALTER TABLE patch_set_approvals CLUSTER ON patch_set_approvals_pkey;

ALTER TABLE account_group_members CLUSTER ON account_group_members_pkey;
ALTER TABLE starred_changes CLUSTER ON starred_changes_pkey;
CLUSTER;


-- Define function for conditional installation of PL/pgSQL.
-- This is required, because starting with PostgreSQL 9.0, PL/pgSQL
-- language is installed by default and database returns error when
-- we try to install it again.
--
-- Source: http://wiki.postgresql.org/wiki/CREATE_OR_REPLACE_LANGUAGE
-- Author: David Fetter
--

delimiter //

CREATE OR REPLACE FUNCTION make_plpgsql()
RETURNS VOID
LANGUAGE SQL
AS $$
CREATE LANGUAGE plpgsql;
$$;

//

delimiter ;

SELECT
    CASE
    WHEN EXISTS(
        SELECT 1
        FROM pg_catalog.pg_language
        WHERE lanname='plpgsql'
    )
    THEN NULL
    ELSE make_plpgsql() END;

DROP FUNCTION make_plpgsql();

-- Define our schema upgrade support function.
--

delimiter //

CREATE OR REPLACE FUNCTION
check_schema_version (exp INT)
RETURNS VARCHAR(255)
AS $$
DECLARE
  l_act INT;
BEGIN
  SELECT version_nbr INTO l_act
  FROM schema_version;

  IF l_act <> exp
  THEN
    RAISE EXCEPTION 'expected schema %, found %', exp, l_act;
  END IF;
  RETURN 'OK';
END;
$$ LANGUAGE plpgsql;
//

delimiter ;

-- Indexes to support @Query
--

-- *********************************************************************
-- AccountAccess
--    covers:             byPreferredEmail, suggestByPreferredEmail
CREATE INDEX accounts_byPreferredEmail
ON accounts (preferred_email);

--    covers:             suggestByFullName
CREATE INDEX accounts_byFullName
ON accounts (full_name);


-- *********************************************************************
-- AccountAgreementAccess
--    @PrimaryKey covers: byAccount


-- *********************************************************************
-- AccountExternalIdAccess
--    covers:             byAccount
CREATE INDEX account_external_ids_byAccount
ON account_external_ids (account_id);

--    covers:             byEmailAddress, suggestByEmailAddress
CREATE INDEX account_external_ids_byEmail
ON account_external_ids (email_address);


-- *********************************************************************
-- AccountGroupAccess
CREATE INDEX account_groups_ownedByGroup
ON account_groups (owner_group_id);


-- *********************************************************************
-- AccountGroupMemberAccess
--    @PrimaryKey covers: byAccount
CREATE INDEX account_group_members_byGroup
ON account_group_members (group_id);


-- *********************************************************************
-- AccountGroupIncludeAccess
--    @PrimaryKey covers: byGroup
CREATE INDEX account_group_includes_byInclude
ON account_group_includes (include_id);


-- *********************************************************************
-- AccountProjectWatchAccess
--    @PrimaryKey covers: byAccount
--    covers:             byProject
CREATE INDEX account_project_watches_byProject
ON account_project_watches (project_name);


-- *********************************************************************
-- AccountSshKeyAccess
--    @PrimaryKey covers: byAccount, valid


-- *********************************************************************
-- ApprovalCategoryAccess
--    too small to bother indexing


-- *********************************************************************
-- ApprovalCategoryValueAccess
--     @PrimaryKey covers: byCategory


-- *********************************************************************
-- AtomicEntry
CREATE UNIQUE INDEX atomic_sourceChange_uk ON atomic_entries (source_change_id);

-- *********************************************************************
-- BranchAccess
--    @PrimaryKey covers: byProject


-- *********************************************************************
-- ChangeAccess
--    covers:             byOwnerOpen
CREATE INDEX changes_byOwnerOpen
ON changes (owner_account_id, created_on, change_id)
WHERE open = 'Y';

--    covers:             byOwnerClosed
CREATE INDEX changes_byOwnerClosed
ON changes (owner_account_id, last_updated_on)
WHERE open = 'N';

--    covers:             submitted, allSubmitted
CREATE INDEX changes_submitted
ON changes (dest_project_name, dest_branch_name, last_updated_on)
WHERE status = 's';

--    covers:             allOpenPrev, allOpenNext
CREATE INDEX changes_allOpen
ON changes (sort_key)
WHERE open = 'Y';

--    covers:             byProjectOpenPrev, byProjectOpenNext
CREATE INDEX changes_byProjectOpen
ON changes (dest_project_name, sort_key)
WHERE open = 'Y';

--    covers:             allClosedPrev, allClosedNext
CREATE INDEX changes_allClosed
ON changes (status, sort_key)
WHERE open = 'N';

--    covers:             byProject
CREATE INDEX changes_byProject
ON changes (dest_project_name);

CREATE INDEX changes_key
ON changes (change_key);


-- *********************************************************************
-- PatchSetApprovalAccess
--    @PrimaryKey covers: byPatchSet, byPatchSetUser
--    covers:             openByUser
CREATE INDEX patch_set_approvals_openByUser
ON patch_set_approvals (account_id)
WHERE change_open = 'Y';

--    covers:             closedByUser
CREATE INDEX patch_set_approvals_closedByUser
ON patch_set_approvals (account_id, change_sort_key)
WHERE change_open = 'N';


-- *********************************************************************
-- ChangeMessageAccess
--    @PrimaryKey covers: byChange


-- *********************************************************************
-- ContributorAgreementAccess
--    covers:             active
CREATE INDEX contributor_agreements_active
ON contributor_agreements (active, short_name);


-- *********************************************************************
-- PatchLineCommentAccess
--    @PrimaryKey covers: published, draft
CREATE INDEX patch_comment_drafts
ON patch_comments (author_id)
WHERE status = 'd';


-- *********************************************************************
-- PatchSetAncestorAccess
--    @PrimaryKey covers: ancestorsOf
--    covers:             descendantsOf
CREATE INDEX patch_set_ancestors_desc
ON patch_set_ancestors (ancestor_revision);


-- *********************************************************************
-- ProjectAccess
--    @PrimaryKey covers: all, suggestByName
--    covers:             ownedByGroup


-- *********************************************************************
-- TrackingIdAccess
--
CREATE INDEX tracking_ids_byTrkId
ON tracking_ids (tracking_id);


-- *********************************************************************
-- StarredChangeAccess
--    @PrimaryKey covers: byAccount

CREATE INDEX starred_changes_byChange
ON starred_changes (change_id);

-- *********************************************************************
-- SubscriptionAccess

CREATE INDEX subscription_access_bySource
ON subscriptions (source_project_name, source_branch_name);
