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

import java.util.Map;

public interface AdminConstants extends Constants {
  String defaultAccountName();
  String defaultAccountGroupName();
  String defaultBranchName();
  String defaultRevisionSpec();

  String buttonDeleteIncludedGroup();
  String buttonAddIncludedGroup();
  String buttonDeleteGroupMembers();
  String buttonAddGroupMember();
  String buttonSaveDescription();
  String buttonRenameGroup();
  String buttonCreateGroup();
  String buttonCreateProject();
  String buttonChangeGroupOwner();
  String buttonChangeGroupType();
  String buttonSelectGroup();
  String buttonSaveChanges();
  String checkBoxEmptyCommit();
  String checkBoxPermissionsOnly();
  String useContentMerge();
  String useContributorAgreements();
  String useSignedOffBy();
  String requireChangeID();
  String headingGroupOptions();
  String isVisibleToAll();
  String buttonSaveGroupOptions();
  String suggestedGroupLabel();
  String parentSuggestions();

  String headingGroupUUID();
  String headingOwner();
  String headingDescription();
  String headingProjectOptions();
  String headingGroupType();
  String headingMembers();
  String headingIncludedGroups();
  String noMembersInfo();
  String headingExternalGroup();
  String headingCreateGroup();
  String headingCreateProject();
  String headingParentProjectName();
  String columnProjectName();
  String headingAgreements();

  String projectSubmitType_FAST_FORWARD_ONLY();
  String projectSubmitType_MERGE_ALWAYS();
  String projectSubmitType_MERGE_IF_NECESSARY();
  String projectSubmitType_CHERRY_PICK();

  String projectState_ACTIVE();
  String projectState_READ_ONLY();
  String projectState_HIDDEN();

  String groupType_SYSTEM();
  String groupType_INTERNAL();
  String groupType_LDAP();

  String columnMember();
  String columnEmailAddress();
  String columnGroupName();
  String columnGroupDescription();
  String columnGroupType();
  String columnGroupNotifications();
  String columnGroupVisibleToAll();

  String columnBranchName();
  String columnBranchRevision();
  String initialRevision();
  String buttonAddBranch();
  String buttonDeleteBranch();
  String branchDeletionOpenChanges();

  String groupListPrev();
  String groupListNext();
  String groupListOpen();

  String groupListTitle();
  String groupTabGeneral();
  String groupTabMembers();
  String projectListTitle();
  String createProjectTitle();
  String projectAdminTabGeneral();
  String projectAdminTabBranches();
  String projectAdminTabAccess();

  String plugins();
  String pluginTabInstalled();

  String columnPluginName();
  String columnPluginVersion();

  String noGroupSelected();
  String errorNoMatchingGroups();
  String errorNoGitRepository();

  String addPermission();
  Map<String,String> permissionNames();

  String refErrorEmpty();
  String refErrorBeginSlash();
  String refErrorDoubleSlash();
  String refErrorNoSpace();
  String refErrorPrintable();
  String errorsMustBeFixed();

  Map<String, String> capabilityNames();

  String sectionTypeReference();
  String sectionTypeSection();
  Map<String, String> sectionNames();
}
