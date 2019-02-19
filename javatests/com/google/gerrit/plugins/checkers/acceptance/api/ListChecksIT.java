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

package com.google.gerrit.plugins.checkers.acceptance.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.plugins.checkers.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checkers.acceptance.testsuite.TestCheckKey;
import com.google.gerrit.plugins.checkers.api.CheckInfo;
import com.google.gerrit.plugins.checkers.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;

public class ListChecksIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;

  @Before
  public void setTimeForTesting() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void listAll() throws Exception {
    TestCheckKey key1 = TestCheckKey.create(project, patchSetId, "my-checker-1");
    checkOperations.newChecker(key1).setState(CheckState.RUNNING).upsert();

    TestCheckKey key2 = TestCheckKey.create(project, patchSetId, "my-checker-2");
    checkOperations.newChecker(key2).setState(CheckState.RUNNING).upsert();

    // TODO(gerrit-team): Use a truth subject to make this assertation proper
    Collection<CheckInfo> info = checksApiFactory.revision(patchSetId).list();
    assertThat(info).hasSize(2);
  }
}
