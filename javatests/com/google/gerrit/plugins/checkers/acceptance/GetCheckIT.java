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

package com.google.gerrit.plugins.checkers.acceptance;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.plugins.checkers.acceptance.testsuite.TestCheckKey;
import com.google.gerrit.plugins.checkers.api.CheckInfo;
import com.google.gerrit.plugins.checkers.api.CheckState;
import com.google.gerrit.reviewdb.client.PatchSet;
import org.junit.Before;
import org.junit.Test;

public class GetCheckIT extends AbstractCheckersTest {

  private PatchSet.Id patchSetId;

  @Before
  public void setUp() throws Exception {
    patchSetId = createChange().getPatchSetId();
  }

  @Test
  public void getCheck() throws Exception {
    String name = "my-checker";
    TestCheckKey key = TestCheckKey.create(project, patchSetId, name);
    checkOperations.newChecker(key).setState(CheckState.RUNNING).upsert();

    CheckInfo info = checksApiFactory.revision(patchSetId).id(name).get();
    assertThat(info.checkerUUID).isEqualTo(name);
    assertThat(info.state).isEqualTo(CheckState.RUNNING);
    assertThat(info.started).isNull();
    assertThat(info.finished).isNull();
    assertThat(info.created).isNotNull();
    assertThat(info.updated).isNotNull();
  }

  @Test
  public void getNonExistingCheckFails() throws Exception {
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: non-existing");

    checksApiFactory.revision(patchSetId).id("non-existing").get();
  }
}
