-- Gerrit 2 : Generic
--

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
-- AccountExternalIdAccess
--    covers:             byAccount
CREATE INDEX account_external_ids_byAccount
ON account_external_ids (account_id);

--    covers:             byEmailAddress, suggestByEmailAddress
CREATE INDEX account_external_ids_byEmail
ON account_external_ids (email_address);


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
-- AccountProjectWatchAccess
--    @PrimaryKey covers: byAccount
--    covers:             byProject
CREATE INDEX account_project_watches_byP
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
-- BranchAccess
--    @PrimaryKey covers: byProject


-- *********************************************************************
-- ChangeAccess
--    covers:             submitted, allSubmitted
CREATE INDEX changes_submitted
ON changes (status, dest_project_name, dest_branch_name, last_updated_on);

--    covers:             byProjectOpenAll
CREATE INDEX changes_byProjectOpen
ON changes (open, dest_project_name, sort_key);

--    covers:             byProject
CREATE INDEX changes_byProject
ON changes (dest_project_name);

CREATE INDEX changes_key
ON changes (change_key);


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
ON patch_comments (status, author_id);


-- *********************************************************************
-- PatchSetAccess
CREATE INDEX patch_sets_byRevision
ON patch_sets (revision);

CREATE UNIQUE INDEX patch_sets_byChangeRev
ON patch_sets (change_id, revision);

-- *********************************************************************
-- PatchSetAncestorAccess
--    @PrimaryKey covers: ancestorsOf
--    covers:             descendantsOf
CREATE INDEX patch_set_ancestors_desc
ON patch_set_ancestors (ancestor_revision);


-- *********************************************************************
-- StarredChangeAccess
--    @PrimaryKey covers: byAccount

CREATE INDEX starred_changes_byChange
ON starred_changes (change_id);

-- *********************************************************************
-- SubmoduleSubscriptionAccess

CREATE INDEX submodule_subscr_acc_byS
ON submodule_subscriptions (submodule_project_name, submodule_branch_name);
