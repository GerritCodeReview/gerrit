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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Relation;
import com.google.gwtorm.client.Schema;
import com.google.gwtorm.client.Sequence;

/**
 * The review service database schema.
 * <p>
 * Root entities that are at the top level of some important data graph:
 * <ul>
 * <li>{@link Account}: Per-user account registration, preferences, identity.</li>
 * <li>{@link Change}: All review information about a single proposed change.</li>
 * <li>{@link SystemConfig}: Server-wide settings, managed by administrator.</li>
 * </ul>
 */
public interface ReviewDb extends Schema {
  /* If you change anything, update SchemaVersion.C to use a new version. */

  @Relation(id = 1)
  SchemaVersionAccess schemaVersion();

  @Relation(id = 2)
  SystemConfigAccess systemConfig();

  @Relation(id = 3)
  ApprovalCategoryAccess approvalCategories();

  @Relation(id = 4)
  ApprovalCategoryValueAccess approvalCategoryValues();

  @Relation(id = 5)
  ContributorAgreementAccess contributorAgreements();

  @Relation(id = 6)
  AccountAccess accounts();

  @Relation(id = 7)
  AccountExternalIdAccess accountExternalIds();

  @Relation(id = 8)
  AccountSshKeyAccess accountSshKeys();

  @Relation(id = 9)
  AccountAgreementAccess accountAgreements();

  @Relation(id = 10)
  AccountGroupAccess accountGroups();

  @Relation(id = 11)
  AccountGroupNameAccess accountGroupNames();

  @Relation(id = 12)
  AccountGroupMemberAccess accountGroupMembers();

  @Relation(id = 13)
  AccountGroupMemberAuditAccess accountGroupMembersAudit();

  @Relation(id = 14)
  AccountGroupIncludeAccess accountGroupIncludes();

  @Relation(id = 15)
  AccountGroupIncludeAuditAccess accountGroupIncludesAudit();

  @Relation(id = 16)
  AccountGroupAgreementAccess accountGroupAgreements();

  @Relation(id = 17)
  AccountDiffPreferenceAccess accountDiffPreferences();

  @Relation(id = 18)
  StarredChangeAccess starredChanges();

  @Relation(id = 19)
  AccountProjectWatchAccess accountProjectWatches();

  @Relation(id = 20)
  AccountPatchReviewAccess accountPatchReviews();

  @Relation(id = 21)
  ChangeAccess changes();

  @Relation(id = 22)
  PatchSetApprovalAccess patchSetApprovals();

  @Relation(id = 23)
  ChangeMessageAccess changeMessages();

  @Relation(id = 24)
  PatchSetAccess patchSets();

  @Relation(id = 25)
  PatchSetAncestorAccess patchSetAncestors();

  @Relation(id = 26)
  PatchLineCommentAccess patchComments();

  @Relation(id = 27)
  TrackingIdAccess trackingIds();

  /** Create the next unique id for an {@link Account}. */
  @Sequence(startWith = 1000000)
  int nextAccountId() throws OrmException;

  /** Create the next unique id for a {@link ContributorAgreement}. */
  @Sequence
  int nextContributorAgreementId() throws OrmException;

  /** Next unique id for a {@link AccountGroup}. */
  @Sequence
  int nextAccountGroupId() throws OrmException;

  /** Next unique id for a {@link Change}. */
  @Sequence
  int nextChangeId() throws OrmException;

  /**
   * Next id for a block of {@link ChangeMessage} records.
   *
   * @see com.google.gerrit.server.ChangeUtil#messageUUID(ReviewDb)
   */
  @Sequence
  int nextChangeMessageId() throws OrmException;
}
