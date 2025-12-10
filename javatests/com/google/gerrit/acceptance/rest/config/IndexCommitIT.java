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

public class IndexCommitIT extends AbstractDaemonTest {

  @Inject private AccountIndexCollection accountIndexCollection;
  @Inject private ChangeIndexCollection changeIndexCollection;
  @Inject private ProjectIndexCollection projectIndexCollection;
  @Inject private GroupIndexCollection groupIndexCollection;

  private int totalCommitCount(Iterable<?> indexes) {
    int total = 0;
    for (Object index : indexes) {
      AbstractFakeIndex<?, ?, ?> fakeIndex = (AbstractFakeIndex<?, ?, ?>) index;
      total += fakeIndex.getCommitCount();
    }
    return total;
  }

  private void assertIndexCommit(String indexName) throws Exception {
    RestResponse response =
        adminRestSession.put(String.format("/config/server/indexes/%s/commit", indexName));

    response.assertOK();
    assertThat(response.getEntityContent()).isEqualTo(")]}'\n\"\"");
  }

  @Test
  public void commitAccountsIndex() throws Exception {
    int initialCommitCount = totalCommitCount(accountIndexCollection.getWriteIndexes());

    assertIndexCommit("accounts");

    int finalCommitCount = totalCommitCount(accountIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void commitChangesIndex() throws Exception {
    int initialCommitCount = totalCommitCount(changeIndexCollection.getWriteIndexes());

    assertIndexCommit("changes");

    int finalCommitCount = totalCommitCount(changeIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void commitProjectsIndex() throws Exception {
    int initialCommitCount = totalCommitCount(projectIndexCollection.getWriteIndexes());

    assertIndexCommit("projects");

    int finalCommitCount = totalCommitCount(projectIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }

  @Test
  public void commitGroupsIndex() throws Exception {
    int initialCommitCount = totalCommitCount(groupIndexCollection.getWriteIndexes());

    assertIndexCommit("groups");

    int finalCommitCount = totalCommitCount(groupIndexCollection.getWriteIndexes());

    assertThat(finalCommitCount).isEqualTo(initialCommitCount + 1);
  }
}
