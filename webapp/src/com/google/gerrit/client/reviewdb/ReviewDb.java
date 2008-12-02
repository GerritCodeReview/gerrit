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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Relation;
import com.google.gwtorm.client.Schema;
import com.google.gwtorm.client.Sequence;

/** The review service database schema. */
public interface ReviewDb extends Schema {
  @Relation
  SystemConfigAccess systemConfig();

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
  AccountAgreementAccess accountAgreements();

  @Relation
  AccountGroupAccess accountGroups();

  @Relation
  AccountGroupMemberAccess accountGroupMembers();

  @Relation
  StarredChangeAccess starredChanges();

  @Relation
  ProjectAccess projects();

  @Relation
  BranchAccess branches();

  @Relation
  ProjectLeadAccountAccess projectLeadAccounts();

  @Relation
  ProjectLeadGroupAccess projectLeadGroups();

  @Relation
  ChangeAccess changes();

  @Relation
  ChangeApprovalAccess changeApprovals();

  @Relation
  PatchSetAccess patchSets();

  @Relation
  PatchSetInfoAccess patchSetInfo();

  @Relation
  PatchAccess patches();

  @Relation
  PatchLineCommentAccess patchComments();

  /** Create the next unique id for an {@link Account}. */
  @Sequence(startWith = 1000000)
  int nextAccountId();

  /** Create the next unique id for a {@link ContributorAgreement}. */
  @Sequence
  int nextContributorAgreementId();

  /** Next unique id for a {@link AccountGroup}. */
  @Sequence
  int nextAccountGroupId();

  /** Next unique id for a {@link Project}. */
  @Sequence
  int nextProjectId();

  /** Next unique id for a {@link Branch}. */
  @Sequence
  int nextBranchId();

  /** Next unique id for a {@link Change}. */
  @Sequence
  int nextChangeId();
}
