// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.common.ProblemInfo;
import com.google.gerrit.reviewdb.client.Change;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

@NoHttpd
public class CheckIT extends AbstractDaemonTest {
  // Most types of tests belong in ConsistencyCheckerTest; these mostly just
  // test paths outside of ConsistencyChecker, like API wiring.
  @Test
  public void currentPatchSetMissing() throws Exception {
    PushOneCommit.Result r = createChange();
    Change c = getChange(r);
    db.patchSets().deleteKeys(Collections.singleton(c.currentPatchSetId()));

    List<ProblemInfo> problems = gApi.changes()
        .id(r.getChangeId())
        .check()
        .problems;
    assertThat(problems).hasSize(1);
    assertThat(problems.get(0).message)
        .isEqualTo("Current patch set 1 not found");
  }

  private Change getChange(PushOneCommit.Result r) throws Exception {
    return db.changes().get(new Change.Id(
        gApi.changes().id(r.getChangeId()).get()._number));
  }
}
