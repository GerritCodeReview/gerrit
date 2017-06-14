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

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.Constants;
import java.util.Map;

public interface AdminConstants extends Constants {
  AdminConstants I = GWT.create(AdminConstants.class);

  String defaultAccountName();

  String defaultAccountGroupName();

  String defaultBranchName();

  String defaultTagName();

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

  String createNewChangeForAllNotInTarget();

  String enableSignedPush();

  String requireSignedPush();

  String requireChangeID();

  String rejectImplicitMerges();

  String enableReviewerByEmail();

  String headingMaxObjectSizeLimit();

  String headingGroupOptions();

  String isVisibleToAll();

  String buttonSaveGroupOptions();

  String suggestedGroupLabel();

  String parentSuggestions();

  String buttonBrowseProjects();

  String projects();

  String projectRepoBrowser();

  String headingGroupUUID();

  String headingOwner();

  String headingDescription();

  String headingProjectOptions();

  String headingProjectCommands();

  String headingCommands();

  String headingMembers();

  String headingIncludedGroups();

  String noMembersInfo();

  String headingExternalGroup();

  String headingCreateGroup();

  String headingParentProjectName();

  String columnProjectName();

  String headingAgreements();

  String headingAuditLog();

  String headingProjectSubmitType();

  String projectSubmitType_FAST_FORWARD_ONLY();

  String projectSubmitType_MERGE_ALWAYS();

  String projectSubmitType_MERGE_IF_NECESSARY();

  String projectSubmitType_REBASE_IF_NECESSARY();

  String projectSubmitType_REBASE_ALWAYS();

  String projectSubmitType_CHERRY_PICK();

  String headingProjectState();

  String projectState_ACTIVE();

  String projectState_READ_ONLY();

  String projectState_HIDDEN();

  String columnMember();

  String columnEmailAddress();

  String columnGroupName();

  String columnGroupDescription();

  String columnGroupType();

  String columnGroupNotifications();

  String columnGroupVisibleToAll();

  String columnDate();

  String columnType();

  String columnByUser();

  String typeAdded();

  String typeRemoved();

  String columnBranchName();

  String columnBranchRevision();

  String columnTagName();

  String columnTagRevision();

  String initialRevision();

  String revision();

  String buttonAddBranch();

  String buttonDeleteBranch();

  String buttonAddTag();

  String buttonDeleteTag();

  String saveHeadButton();

  String cancelHeadButton();

  String groupItemHelp();

  String groupListTitle();

  String groupFilter();

  String createGroupTitle();

  String groupTabGeneral();

  String groupTabMembers();

  String groupTabAuditLog();

  String projectListTitle();

  String projectFilter();

  String createProjectTitle();

  String projectListQueryLink();

  String plugins();

  String pluginEnabled();

  String pluginDisabled();

  String pluginSettingsToolTip();

  String columnPluginName();

  String columnPluginSettings();

  String columnPluginVersion();

  String columnPluginStatus();

  String noGroupSelected();

  String errorNoMatchingGroups();

  String errorNoGitRepository();

  String addPermission();

  Map<String, String> permissionNames();

  String refErrorEmpty();

  String refErrorBeginSlash();

  String refErrorDoubleSlash();

  String refErrorNoSpace();

  String refErrorPrintable();

  String errorsMustBeFixed();

  String sectionTypeReference();

  String sectionTypeSection();

  Map<String, String> sectionNames();

  String pagedListPrev();

  String pagedListNext();

  String buttonCreate();

  String buttonCreateDescription();

  String buttonCreateChange();

  String buttonCreateChangeDescription();

  String buttonEditConfig();

  String buttonEditConfigDescription();

  String editConfigMessage();
}
