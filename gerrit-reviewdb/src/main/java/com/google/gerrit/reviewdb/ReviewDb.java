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

  @Relation
  SchemaVersionAccess schemaVersion();

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
  AccountSshKeyAccess accountSshKeys();

  @Relation
  AccountAgreementAccess accountAgreements();

  @Relation
  AccountGroupAccess accountGroups();

  @Relation
  AccountGroupNameAccess accountGroupNames();

  @Relation
  AccountGroupMemberAccess accountGroupMembers();

  @Relation
  AccountGroupMemberAuditAccess accountGroupMembersAudit();

  @Relation
  AccountGroupIncludeAccess accountGroupIncludes();

  @Relation
  AccountGroupIncludeAuditAccess accountGroupIncludesAudit();

  @Relation
  AccountGroupAgreementAccess accountGroupAgreements();

  @Relation
  AccountDiffPreferenceAccess accountDiffPreferences();

  @Relation
  StarredChangeAccess starredChanges();

  @Relation
  AccountProjectWatchAccess accountProjectWatches();

  @Relation
  AccountPatchReviewAccess accountPatchReviews();

  @Relation
  SubscriptionAccess subscriptions();

  @Relation
  ChangeAccess changes();

  @Relation
  PatchSetApprovalAccess patchSetApprovals();

  @Relation
  ChangeMessageAccess changeMessages();

  @Relation
  PatchSetAccess patchSets();

  @Relation
  PatchSetAncestorAccess patchSetAncestors();

  @Relation
  PatchLineCommentAccess patchComments();

  @Relation
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
