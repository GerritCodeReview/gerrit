// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static java.util.stream.Collectors.toList;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ChangeIndexedCounter;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.change.IndexOperations;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public abstract class AbstractIndexTests extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private IndexOperations.Change changeIndexOperations;

  @Test
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  public void indexChange() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {

      PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
      String changeId = change.getChangeId();
      String changeLegacyId = change.getChange().getId().toString();
      ChangeInfo changeInfo = gApi.changes().id(changeId).get();

      try (AutoCloseable ignored = changeIndexOperations.disableWrites()) {
        amendChange(changeId, "second test", "test2.txt", "test2");
        assertChangeQuery(change.getChange(), false);
      }

      changeIndexedCounter.clear();
      String cmd = Joiner.on(" ").join("gerrit", "index", "changes", changeLegacyId);
      adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();

      changeIndexedCounter.assertReindexOf(changeInfo, 1);

      assertChangeQuery(change.getChange(), true);
    }
  }

  @Test
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  public void indexProject() throws Exception {
    ChangeIndexedCounter changeIndexedCounter = new ChangeIndexedCounter();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedCounter)) {

      PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
      String changeId = change.getChangeId();
      ChangeInfo changeInfo = gApi.changes().id(changeId).get();

      try (AutoCloseable ignored = changeIndexOperations.disableWrites()) {
        amendChange(changeId, "second test", "test2.txt", "test2");
        assertChangeQuery(change.getChange(), false);
      }

      changeIndexedCounter.clear();
      String cmd = Joiner.on(" ").join("gerrit", "index", "changes-in-project", project.get());
      adminSshSession.exec(cmd);
      adminSshSession.assertSuccess();

      boolean indexing = true;
      while (indexing) {
        String out = adminSshSession.exec("gerrit show-queue --wide");
        adminSshSession.assertSuccess();
        indexing = out.contains("Index all changes of project " + project.get());
      }

      changeIndexedCounter.assertReindexOf(changeInfo, 1);

      assertChangeQuery(change.getChange(), true);
    }
  }

  private void assertChangeQuery(ChangeData change, boolean assertTrue) throws Exception {
    List<Integer> ids = query("message:second").stream().map(c -> c._number).collect(toList());
    if (assertTrue) {
      assertThat(ids).contains(change.getId().get());
    } else {
      assertThat(ids).doesNotContain(change.getId().get());
    }
  }
}
