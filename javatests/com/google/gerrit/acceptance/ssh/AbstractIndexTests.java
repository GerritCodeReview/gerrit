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
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseSsh
@UseLocalDisk
public abstract class AbstractIndexTests extends AbstractDaemonTest {
  @Inject private DynamicSet<ChangeIndexedListener> changeIndexedListeners;

  private ChangeIndexedCounter changeIndexedCounter;
  private RegistrationHandle changeIndexedCounterHandle;

  /** @param injector injector */
  public abstract void configureIndex(Injector injector) throws Exception;

  @Before
  public void addChangeIndexedCounter() {
    changeIndexedCounter = new ChangeIndexedCounter();
    changeIndexedCounterHandle = changeIndexedListeners.add("gerrit", changeIndexedCounter);
  }

  @After
  public void removeChangeIndexedCounter() {
    if (changeIndexedCounterHandle != null) {
      changeIndexedCounterHandle.remove();
    }
  }

  @Test
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  public void indexChange() throws Exception {
    configureIndex(server.getTestInjector());

    PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
    String changeId = change.getChangeId();
    String changeLegacyId = change.getChange().getId().toString();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();

    disableChangeIndexWrites();
    amendChange(changeId, "second test", "test2.txt", "test2");

    assertChangeQuery(change.getChange(), false);
    enableChangeIndexWrites();

    changeIndexedCounter.clear();
    String cmd = Joiner.on(" ").join("gerrit", "index", "changes", changeLegacyId);
    adminSshSession.exec(cmd);
    adminSshSession.assertSuccess();

    changeIndexedCounter.assertReindexOf(changeInfo, 1);

    assertChangeQuery(change.getChange(), true);
  }

  @Test
  @GerritConfig(name = "index.autoReindexIfStale", value = "false")
  public void indexProject() throws Exception {
    configureIndex(server.getTestInjector());

    PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
    String changeId = change.getChangeId();
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();

    disableChangeIndexWrites();
    amendChange(changeId, "second test", "test2.txt", "test2");

    assertChangeQuery(change.getChange(), false);
    enableChangeIndexWrites();

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

  private void assertChangeQuery(ChangeData change, boolean assertTrue) throws Exception {
    List<Integer> ids = query("message:second").stream().map(c -> c._number).collect(toList());
    if (assertTrue) {
      assertThat(ids).contains(change.getId().get());
    } else {
      assertThat(ids).doesNotContain(change.getId().get());
    }
  }
}
