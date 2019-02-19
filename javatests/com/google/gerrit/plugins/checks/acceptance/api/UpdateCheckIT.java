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

package com.google.gerrit.plugins.checks.acceptance.api;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.plugins.checks.acceptance.AbstractCheckersTest;
import com.google.gerrit.plugins.checks.acceptance.testsuite.TestCheckKey;
import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import org.junit.Before;
import org.junit.Test;

public class UpdateCheckIT extends AbstractCheckersTest {
  private PatchSet.Id patchSetId;

  @Before
  public void setTimeForTesting() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void updateCheckState() throws Exception {
    TestCheckKey key = TestCheckKey.create(project, patchSetId, "my-checker-1");
    checkOperations.newChecker(key).setState(CheckState.RUNNING).upsert();

    CheckInput input = new CheckInput();
    input.state = CheckState.FAILED;

    CheckInfo info = checksApiFactory.revision(patchSetId).id("my-checker-1").update(input);
    assertThat(info.state).isEqualTo(CheckState.FAILED);
  }
}
