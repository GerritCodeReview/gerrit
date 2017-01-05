// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server;

import static com.google.common.truth.Truth.assertThat;

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

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class ChangeUtilTest {
  @Test
  public void changeMessageUuid() throws Exception {
    Pattern pat = Pattern.compile("^[0-9a-f]{8}_[0-9a-f]{8}$");
    assertThat("abcd1234_0987fedc").matches(pat);

    ReviewDb db = new FakeDb();
    String id1 = ChangeUtil.messageUUID(db);
    assertThat(id1).matches(pat);

    String id2 = ChangeUtil.messageUUID(db);
    assertThat(id2).isNotEqualTo(id1);
    assertThat(id2).matches(pat);
  }

  private static class FakeDb implements ReviewDb {
    private final AtomicInteger SEQ = new AtomicInteger(100);

    @Override
    public int nextChangeMessageId() {
      return SEQ.getAndIncrement();
    }

    @Override
    public void commit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void rollback() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void updateSchema(StatementExecutor e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void pruneSchema(StatementExecutor e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Access<?, ?>[] allRelations() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SchemaVersionAccess schemaVersion() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SystemConfigAccess systemConfig() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountAccess accounts() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountExternalIdAccess accountExternalIds() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupAccess accountGroups() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupNameAccess accountGroupNames() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupMemberAccess accountGroupMembers() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupMemberAuditAccess accountGroupMembersAudit() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeAccess changes() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PatchSetApprovalAccess patchSetApprovals() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ChangeMessageAccess changeMessages() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PatchSetAccess patchSets() {
      throw new UnsupportedOperationException();
    }

    @Override
    public PatchLineCommentAccess patchComments() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupByIdAccess accountGroupById() {
      throw new UnsupportedOperationException();
    }

    @Override
    public AccountGroupByIdAudAccess accountGroupByIdAud() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int nextAccountId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int nextAccountGroupId() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int nextChangeId() {
      throw new UnsupportedOperationException();
    }
  }
}
