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
import com.google.gerrit.reviewdb.server.AccountExternalIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAccess;
import com.google.gerrit.reviewdb.server.AccountGroupByIdAudAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAccess;
import com.google.gerrit.reviewdb.server.AccountGroupMemberAuditAccess;
import com.google.gerrit.reviewdb.server.AccountGroupNameAccess;
import com.google.gerrit.reviewdb.server.ChangeAccess;
import com.google.gerrit.reviewdb.server.ChangeMessageAccess;
import com.google.gerrit.reviewdb.server.PatchLineCommentAccess;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gerrit.reviewdb.server.PatchSetApprovalAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.SchemaVersionAccess;
import com.google.gerrit.reviewdb.server.SystemConfigAccess;
import com.google.gwtorm.server.Access;
import com.google.gwtorm.server.StatementExecutor;

/** ReviewDb that is disabled for testing. */
public class DisabledReviewDb implements ReviewDb {
  public static class Disabled extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private Disabled() {
      super("ReviewDb is disabled for this test");
    }
  }

  @Override
  public void close() {
    // Do nothing.
  }

  @Override
  public void commit() {
    throw new Disabled();
  }

  @Override
  public void rollback() {
    throw new Disabled();
  }

  @Override
  public void updateSchema(StatementExecutor e) {
    throw new Disabled();
  }

  @Override
  public void pruneSchema(StatementExecutor e) {
    throw new Disabled();
  }

  @Override
  public Access<?, ?>[] allRelations() {
    throw new Disabled();
  }

  @Override
  public SchemaVersionAccess schemaVersion() {
    throw new Disabled();
  }

  @Override
  public SystemConfigAccess systemConfig() {
    throw new Disabled();
  }

  @Override
  public AccountAccess accounts() {
    throw new Disabled();
  }

  @Override
  public AccountExternalIdAccess accountExternalIds() {
    throw new Disabled();
  }

  @Override
  public AccountGroupAccess accountGroups() {
    throw new Disabled();
  }

  @Override
  public AccountGroupNameAccess accountGroupNames() {
    throw new Disabled();
  }

  @Override
  public AccountGroupMemberAccess accountGroupMembers() {
    throw new Disabled();
  }

  @Override
  public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
    throw new Disabled();
  }

  @Override
  public ChangeAccess changes() {
    throw new Disabled();
  }

  @Override
  public PatchSetApprovalAccess patchSetApprovals() {
    throw new Disabled();
  }

  @Override
  public ChangeMessageAccess changeMessages() {
    throw new Disabled();
  }

  @Override
  public PatchSetAccess patchSets() {
    throw new Disabled();
  }

  @Override
  public PatchLineCommentAccess patchComments() {
    throw new Disabled();
  }

  @Override
  public AccountGroupByIdAccess accountGroupById() {
    throw new Disabled();
  }

  @Override
  public AccountGroupByIdAudAccess accountGroupByIdAud() {
    throw new Disabled();
  }

  @Override
  public int nextAccountId() {
    throw new Disabled();
  }

  @Override
  public int nextAccountGroupId() {
    throw new Disabled();
  }

  @Override
  public int nextChangeId() {
    throw new Disabled();
  }
}
