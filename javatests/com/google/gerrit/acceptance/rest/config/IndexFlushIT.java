// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.index.project.ProjectIndexCollection;
import com.google.gerrit.index.testing.AbstractFakeIndex;
import com.google.gerrit.server.index.account.AccountIndexCollection;
import com.google.gerrit.server.index.change.ChangeIndexCollection;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.Inject;
import org.junit.Test;

public class IndexFlushIT extends AbstractDaemonTest {

  @Inject private AccountIndexCollection accountIndexCollection;
  @Inject private ChangeIndexCollection changeIndexCollection;
  @Inject private ProjectIndexCollection projectIndexCollection;
  @Inject private GroupIndexCollection groupIndexCollection;

  @Test
  public void flushAndCommitAccountsIndex() throws Exception {
    int initialCommitCount = totalFlushAndCommitCount(accountIndexCollection.getWriteIndexes());

    assertIndexFlushAndCommit("accounts");

    int finalCommitCount = totalFlushAndCommitCount(accountIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void flushAndCommitChangesIndex() throws Exception {
    int initialCommitCount = totalFlushAndCommitCount(changeIndexCollection.getWriteIndexes());

    assertIndexFlushAndCommit("changes");

    int finalCommitCount = totalFlushAndCommitCount(changeIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void flushAndCommitProjectsIndex() throws Exception {
    int initialCommitCount = totalFlushAndCommitCount(projectIndexCollection.getWriteIndexes());

    assertIndexFlushAndCommit("projects");

    int finalCommitCount = totalFlushAndCommitCount(projectIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void flushAndCommitGroupsIndex() throws Exception {
    int initialCommitCount = totalFlushAndCommitCount(groupIndexCollection.getWriteIndexes());

    assertIndexFlushAndCommit("groups");

    int finalCommitCount = totalFlushAndCommitCount(groupIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void flushAndCommitInvalidIndex() throws Exception {
    RestResponse response = adminRestSession.post("/config/server/indexes/invalidIndex/flush");

    response.assertNotFound();
  }

  @Test
  public void flushAndCommitForbiddenForUnauthorisedUsers() throws Exception {
    RestResponse response = userRestSession.post("/config/server/indexes/changes/flush");

    response.assertForbidden();
  }

  private int totalFlushAndCommitCount(Iterable<?> indexes) {
    int total = 0;
    for (Object index : indexes) {
      AbstractFakeIndex<?, ?, ?> fakeIndex = (AbstractFakeIndex<?, ?, ?>) index;
      total += fakeIndex.getFlushAndCommitCount();
    }
    return total;
  }

  private void assertIndexFlushAndCommit(String indexName) throws Exception {
    RestResponse response =
        adminRestSession.post(String.format("/config/server/indexes/%s/flush", indexName));

    response.assertNoContent();
  }
}
