// Copyright (C) 2008 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.admin;

import com.google.gwt.i18n.client.Constants;

public interface AdminConstants extends Constants {
  String defaultAccountName();
  String defaultAccountGroupName();
  String defaultBranchName();
  String defaultRevisionSpec();

  String buttonAddIncludedGroup();
  String buttonDeleteGroupMembers();
  String buttonAddGroupMember();
  String buttonSaveDescription();
  String buttonRenameGroup();
  String buttonCreateGroup();
  String buttonChangeGroupOwner();
  String buttonChangeGroupType();
  String buttonSelectGroup();
  String buttonAddProjectRight();
  String buttonClearProjectRight();
  String buttonSaveChanges();
  String useContentMerge();
  String useContributorAgreements();
  String useSignedOffBy();
  String requireChangeID();

  String headingOwner();
  String headingParentProjectName();
  String headingDescription();
  String headingProjectOptions();
  String headingGroupType();
  String headingMembers();
  String headingIncludedGroups();
  String headingExternalGroup();
  String headingCreateGroup();
  String headingAccessRights();
  String headingAgreements();

  String projectSubmitType_FAST_FORWARD_ONLY();
  String projectSubmitType_MERGE_ALWAYS();
  String projectSubmitType_MERGE_IF_NECESSARY();
  String projectSubmitType_CHERRY_PICK();

  String groupType_SYSTEM();
  String groupType_INTERNAL();
  String groupType_LDAP();

  String columnMember();
  String columnEmailAddress();
  String columnGroupName();
  String columnProjectName();
  String columnGroupDescription();
  String columnProjectDescription();
  String columnRightOrigin();
  String columnApprovalCategory();
  String columnRightRange();
  String columnRefName();

  String columnBranchName();
  String columnBranchRevision();
  String initialRevision();
  String buttonAddBranch();
  String buttonDeleteBranch();

  String projectListPrev();
  String projectListNext();
  String projectListOpen();

  String groupListPrev();
  String groupListNext();
  String groupListOpen();

  String groupListTitle();
  String projectListTitle();
  String projectAdminTabGeneral();
  String projectAdminTabBranches();
  String projectAdminTabAccess();

  String noGroupSelected();
  String errorNoMatchingGroups();
  String errorNoGitRepository();
}
