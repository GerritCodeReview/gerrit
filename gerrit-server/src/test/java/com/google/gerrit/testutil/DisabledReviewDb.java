// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.testutil;

import com.google.gerrit.reviewdb.server.AccountAccess;
import com.google.gerrit.reviewdb.server.AccountDiffPreferenceAccess;
import com.google.gerrit.reviewdb.server.AccountExternalIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAudAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAuditAccess;
import com.google.gerrit.reviewdb.server.AccountGroupNameAccess;
import com.google.gerrit.reviewdb.server.AccountPatchReviewAccess;
import com.google.gerrit.reviewdb.server.AccountProjectWatchAccess;
import com.google.gerrit.reviewdb.server.AccountSshKeyAccess;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SchemaVersionAccess;
import com.google.gerrit.reviewdb.server.StarredChangeAccess;
import com.google.gerrit.reviewdb.server.SubmoduleSubscriptionAccess;
import com.google.gerrit.reviewdb.server.SystemConfigAccess;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.StatementExecutor;

/** ReviewDb that is disabled for testing. */
public class DisabledReviewDb implements ReviewDb {
  public static final String MESSAGE = "ReviewDb is disabled for this test";

  @Override
  public void close() {
    // Do nothing.
  }

  @Override
  public void commit() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void rollback() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void updateSchema(StatementExecutor e) {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SystemConfigAccess systemConfig() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountAccess accounts() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountExternalIdAccess accountExternalIds() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountSshKeyAccess accountSshKeys() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupAccess accountGroups() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountDiffPreferenceAccess accountDiffPreferences() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public StarredChangeAccess starredChanges() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountProjectWatchAccess accountProjectWatches() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountPatchReviewAccess accountPatchReviews() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public ChangeAccess changes() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchSetAccess patchSets() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public SubmoduleSubscriptionAccess submoduleSubscriptions() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextAccountId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextAccountGroupId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextChangeId() {
    throw new AssertionError(MESSAGE);
  }

  @Override
  public int nextChangeMessageId() {
    throw new AssertionError(MESSAGE);
  }
}
