// Copyright (C) 2019 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.server.restapi.config.IndexChanges;
import org.apache.http.HttpStatus;
import org.junit.Test;

public class IndexChangesIT extends AbstractDaemonTest {

  @Test
  public void indexRequestFromNonAdminRejected() throws Exception {
    String changeId = createChange().getChangeId();
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = ImmutableSet.of(changeId);
    userRestSession.post("/config/server/index.changes", in).assertForbidden();
  }

  @Test
  public void indexVisibleChange() throws Exception {
    String changeId = createChange().getChangeId();
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = ImmutableSet.of(changeId);
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
  }

  @Test
  public void indexNonVisibleChange() throws Exception {
    String changeId = createChange().getChangeId();
    blockRead("refs/heads/master");
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = ImmutableSet.of(changeId);
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
  }

  @Test
  public void indexMultipleChange() throws Exception {
    ImmutableSet.Builder<String> changeIds = ImmutableSet.builder();
    for (int i = 0; i < 10; i++) {
      changeIds.add(createChange().getChangeId());
    }
    IndexChanges.Input in = new IndexChanges.Input();
    in.changes = changeIds.build();
    adminRestSession.post("/config/server/index.changes", in).assertStatus(HttpStatus.SC_ACCEPTED);
  }
}
