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

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Injector;
import java.util.List;
import org.junit.Test;

@NoHttpd
@UseSsh
public abstract class AbstractIndexTests extends AbstractDaemonTest {
  /** @param injector injector */
  public abstract void configureIndex(Injector injector) throws Exception;

  @Test
  public void indexChange() throws Exception {
    configureIndex(server.getTestInjector());

    PushOneCommit.Result change = createChange("first change", "test1.txt", "test1");
    String changeId = change.getChangeId();
    String changeLegacyId = change.getChange().getId().toString();

    disableChangeIndexWrites();
    amendChange(changeId, "second test", "test2.txt", "test2");

    assertChangeQuery("message:second", change.getChange(), false);
    enableChangeIndexWrites();

    String cmd = Joiner.on(" ").join("gerrit", "index", "changes", changeLegacyId);
    adminSshSession.exec(cmd);

    assertChangeQuery("message:second", change.getChange(), true);
  }

  protected void assertChangeQuery(String q, ChangeData change, Boolean assertTrue)
      throws Exception {
    List<ChangeInfo> result = query(q);
    Iterable<Integer> ids = ids(result);
    if (assertTrue) {
      assertThat(ids).contains(change.getId().get());
    } else {
      assertThat(ids).doesNotContain(change.getId().get());
    }
  }

  protected static Iterable<Integer> ids(Iterable<ChangeInfo> changes) {
    return FluentIterable.from(changes).transform(in -> in._number);
  }
}
