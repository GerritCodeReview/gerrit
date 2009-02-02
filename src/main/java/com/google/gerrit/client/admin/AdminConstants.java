// Copyright 2008 Google Inc.
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

  String buttonDeleteGroupMembers();
  String buttonAddGroupMember();
  String buttonSaveDescription();
  String buttonRenameGroup();
  String buttonCreateGroup();
  String buttonChangeGroupOwner();
  String buttonAddProjectRight();

  String headingOwner();
  String headingDescription();
  String headingMembers();
  String headingCreateGroup();
  String headingAccessRights();

  String columnMember();
  String columnEmailAddress();
  String columnGroupName();
  String columnProjectName();
  String columnGroupDescription();
  String columnProjectDescription();
  String columnApprovalCategory();
  String columnRightRange();

  String groupListTitle();
  String projectListTitle();
}
