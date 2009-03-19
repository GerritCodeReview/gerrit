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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Relation;
import com.google.gwtorm.client.Schema;
import com.google.gwtorm.client.Sequence;

/** The review service database schema. */
public interface ReviewDb extends Schema {
  public static final int VERSION = 7;

  @Relation
  SchemaVersionAccess schemaVersion();

  @Relation
  SystemConfigAccess systemConfig();

  @Relation
  TrustedExternalIdAccess trustedExternalIds();

  @Relation
  ApprovalCategoryAccess approvalCategories();

  @Relation
  ApprovalCategoryValueAccess approvalCategoryValues();

  @Relation
  ContributorAgreementAccess contributorAgreements();

  @Relation
  AccountAccess accounts();

  @Relation
  AccountExternalIdAccess accountExternalIds();

  @Relation
  AccountSshKeyAccess accountSshKeys();

  @Relation
  AccountAgreementAccess accountAgreements();

  @Relation
  AccountGroupAccess accountGroups();

  @Relation
  AccountGroupMemberAccess accountGroupMembers();

  @Relation
  StarredChangeAccess starredChanges();

  @Relation
  AccountProjectWatchAccess accountProjectWatches();

  @Relation
  ProjectAccess projects();

  @Relation
  ProjectRightAccess projectRights();

  @Relation
  BranchAccess branches();

  @Relation
  ChangeAccess changes();

  @Relation
  ChangeApprovalAccess changeApprovals();

  @Relation
  ChangeMessageAccess changeMessages();

  @Relation
  PatchSetAccess patchSets();

  @Relation
  PatchSetInfoAccess patchSetInfo();

  @Relation
  PatchSetAncestorAccess patchSetAncestors();

  @Relation
  PatchAccess patches();

  @Relation
  PatchContentAccess patchContents();

  @Relation
  PatchLineCommentAccess patchComments();

  /** Create the next unique id for an {@link Account}. */
  @Sequence(startWith = 1000000)
  int nextAccountId() throws OrmException;

  /** Create the next unique id for a {@link ContributorAgreement}. */
  @Sequence
  int nextContributorAgreementId() throws OrmException;

  /** Next unique id for a {@link AccountGroup}. */
  @Sequence
  int nextAccountGroupId() throws OrmException;

  /** Next unique id for a {@link Project}. */
  @Sequence
  int nextProjectId() throws OrmException;

  /** Next unique id for a {@link Branch}. */
  @Sequence
  int nextBranchId() throws OrmException;

  /** Next unique id for a {@link Change}. */
  @Sequence
  int nextChangeId() throws OrmException;

  @Sequence
  int nextChangeMessageId() throws OrmException;
}
