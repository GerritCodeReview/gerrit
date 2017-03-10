-- Gerrit 2 : PostgreSQL
--

-- Cluster hot tables by their primary method of access
--
ALTER TABLE patch_sets CLUSTER ON patch_sets_pkey;
ALTER TABLE change_messages CLUSTER ON change_messages_pkey;
ALTER TABLE patch_comments CLUSTER ON patch_comments_pkey;
ALTER TABLE patch_set_approvals CLUSTER ON patch_set_approvals_pkey;

ALTER TABLE account_group_members CLUSTER ON account_group_members_pkey;
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

delimiter ;

-- Indexes to support @Query
--

-- *********************************************************************
-- AccountGroupMemberAccess
--    @PrimaryKey covers: byAccount
CREATE INDEX account_group_members_byGroup
ON account_group_members (group_id);


-- *********************************************************************
-- AccountGroupByIdAccess
--    @PrimaryKey covers: byGroup
CREATE INDEX account_group_id_byInclude
ON account_group_by_id (include_uuid);


-- *********************************************************************
-- ApprovalCategoryAccess
--    too small to bother indexing


-- *********************************************************************
-- ApprovalCategoryValueAccess
--     @PrimaryKey covers: byCategory


-- *********************************************************************
-- BranchAccess
--    @PrimaryKey covers: byProject


-- *********************************************************************
-- ChangeMessageAccess
--    @PrimaryKey covers: byChange

--    covers:             byPatchSet
CREATE INDEX change_messages_byPatchset
ON change_messages (patchset_change_id, patchset_patch_set_id);

-- *********************************************************************
-- PatchLineCommentAccess
--    @PrimaryKey covers: published, draft
CREATE INDEX patch_comment_drafts
ON patch_comments (author_id)
WHERE status = 'd';


-- *********************************************************************
-- PatchSetAccess
CREATE INDEX patch_sets_byRevision
ON patch_sets (revision);
