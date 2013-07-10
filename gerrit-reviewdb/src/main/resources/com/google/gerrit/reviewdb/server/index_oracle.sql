-- Gerrit 2 : Oracle
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

--    covers:             byBranchClosedPrev, byBranchClosedNext
CREATE INDEX changes_byBranchClosed
ON changes (status, dest_project_name, dest_branch_name, sort_key)
WHERE open = 'N';

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
CREATE INDEX tracking_ids_byTrkKey
ON tracking_ids (tracking_key);


-- *********************************************************************
-- StarredChangeAccess
--    @PrimaryKey covers: byAccount

CREATE INDEX starred_changes_byChange
ON starred_changes (change_id);

-- *********************************************************************
-- SubmoduleSubscriptionAccess

CREATE INDEX submodule_subscription_access_bySubscription
ON submodule_subscriptions (submodule_project_name, submodule_branch_name);
