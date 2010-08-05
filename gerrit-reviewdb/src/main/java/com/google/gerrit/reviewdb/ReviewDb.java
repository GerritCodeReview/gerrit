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
 * <li>{@link Project}: Configuration for a single Git repository.</li>
 * <li>{@link Account}: Per-user account registration, preferences, identity.</li>
 * <li>{@link Change}: All review information about a single proposed change.</li>
 * <li>{@link SystemConfig}: Server-wide settings, managed by administrator.</li>
 * </ul>
 */
public interface ReviewDb extends Schema {
  /* If you change anything, update SchemaVersion.C to use a new version. */

  @Relation(id = 0)
  SchemaVersionAccess schemaVersion();

  @Relation(id = 1)
  SystemConfigAccess systemConfig();

  @Relation(id = 2)
  ApprovalCategoryAccess approvalCategories();

  @Relation(id = 3)
  ApprovalCategoryValueAccess approvalCategoryValues();

  @Relation(id = 4)
  ContributorAgreementAccess contributorAgreements();

  @Relation(id = 5)
  AccountAccess accounts();

  @Relation(id = 6)
  AccountExternalIdAccess accountExternalIds();

  @Relation(id = 7)
  AccountSshKeyAccess accountSshKeys();

  @Relation(id = 8)
  AccountAgreementAccess accountAgreements();

  @Relation(id = 9)
  AccountGroupAccess accountGroups();

  @Relation(id = 10)
  AccountGroupNameAccess accountGroupNames();

  @Relation(id = 11)
  AccountGroupMemberAccess accountGroupMembers();

  @Relation(id = 12)
  AccountGroupMemberAuditAccess accountGroupMembersAudit();

  @Relation(id = 13)
  AccountGroupAgreementAccess accountGroupAgreements();

  @Relation(id = 14)
  AccountDiffPreferenceAccess accountDiffPreferences();

  @Relation(id = 15)
  StarredChangeAccess starredChanges();

  @Relation(id = 16)
  AccountProjectWatchAccess accountProjectWatches();

  @Relation(id = 17)
  AccountPatchReviewAccess accountPatchReviews();

  @Relation(id = 18)
  ProjectAccess projects();

  @Relation(id = 19)
  ChangeAccess changes();

  @Relation(id = 20)
  PatchSetApprovalAccess patchSetApprovals();

  @Relation(id = 21)
  ChangeMessageAccess changeMessages();

  @Relation(id = 22)
  PatchSetAccess patchSets();

  @Relation(id = 23)
  PatchSetAncestorAccess patchSetAncestors();

  @Relation(id = 24)
  PatchLineCommentAccess patchComments();

  @Relation(id = 25)
  RefRightAccess refRights();

  @Relation(id = 26)
  TrackingIdAccess trackingIds();

  @Relation(id = 27)
  ActiveSessionAccess activeSessions();

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
