// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.Set;

public class ConflictsOperatorIT extends AbstractDaemonTest {

  private int count;

  @Test
  public void noConflictingChanges() throws Exception {
    PushOneCommit.Result change = createChange(true);
    createChange(false);

    Set<String> changes = queryConflictingChanges(change);
    assertThat((Iterable<?>)changes).isEmpty();
  }

  @Test
  public void conflictingChanges() throws Exception {
    PushOneCommit.Result change = createChange(true);
    PushOneCommit.Result conflictingChange1 = createChange(true);
    PushOneCommit.Result conflictingChange2 = createChange(true);
    createChange(false);

    Set<String> changes = queryConflictingChanges(change);
    assertChanges(changes, conflictingChange1, conflictingChange2);
  }

  private PushOneCommit.Result createChange(boolean conflicting)
      throws Exception {
    testRepo.reset("origin/master");
    String file = conflicting ? "test.txt" : "test-" + count + ".txt";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, "Change " + count, file,
            "content " + count);
    count++;
    return push.to("refs/for/master");
  }

  private Set<String> queryConflictingChanges(PushOneCommit.Result change)
      throws IOException {
    RestResponse r =
        adminSession.get("/changes/?q=conflicts:" + change.getChangeId());
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    Set<ChangeInfo> changes =
        newGson().fromJson(r.getReader(),
            new TypeToken<Set<ChangeInfo>>() {}.getType());
    r.consume();
    return ImmutableSet.copyOf(Iterables.transform(changes,
        new Function<ChangeInfo, String>() {
          @Override
          public String apply(ChangeInfo input) {
            return input.id;
          }
        }));
  }

  private void assertChanges(Set<String> actualChanges,
      PushOneCommit.Result... expectedChanges) {
    assertThat((Iterable<?>)actualChanges).hasSize(expectedChanges.length);
    for (PushOneCommit.Result c : expectedChanges) {
      assertThat(actualChanges.contains(id(c))).isTrue();
    }
  }

  private String id(PushOneCommit.Result change) {
    return project.get() + "~master~" + change.getChangeId();
  }
}
