// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testutil.TestChanges;
import org.junit.Test;

public class ChangeDataTest {
  @Test
  public void setPatchSetsClearsCurrentPatchSet() throws Exception {
    Project.NameKey project = new Project.NameKey("project");
    ChangeData cd = ChangeData.createForTest(project, new Change.Id(1), 1);
    cd.setChange(TestChanges.newChange(project, new Account.Id(1000)));
    PatchSet curr1 = cd.currentPatchSet();
    int currId = curr1.getId().get();
    PatchSet ps1 = new PatchSet(new PatchSet.Id(cd.getId(), currId + 1));
    PatchSet ps2 = new PatchSet(new PatchSet.Id(cd.getId(), currId + 2));
    cd.setPatchSets(ImmutableList.of(ps1, ps2));
    PatchSet curr2 = cd.currentPatchSet();
    assertThat(curr2).isNotSameAs(curr1);
  }
}
