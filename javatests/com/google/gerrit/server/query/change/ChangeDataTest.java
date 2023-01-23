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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.TestChanges;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ChangeDataTest {
  @Test
  public void setPatchSetsClearsCurrentPatchSet() throws Exception {
    Project.NameKey project = Project.nameKey("project");
    ChangeData cd = ChangeData.createForTest(project, Change.id(1), 1, ObjectId.zeroId());
    cd.setChange(TestChanges.newChange(project, Account.id(1000)));
    PatchSet curr1 = cd.currentPatchSet();
    int currId = curr1.id().get();
    PatchSet ps1 = newPatchSet(cd.getId(), currId + 1);
    PatchSet ps2 = newPatchSet(cd.getId(), currId + 2);
    cd.setPatchSets(ImmutableList.of(ps1, ps2));
    PatchSet curr2 = cd.currentPatchSet();
    assertThat(curr2).isNotSameInstanceAs(curr1);
  }

  private static PatchSet newPatchSet(Change.Id changeId, int num) {
    return PatchSet.builder()
        .id(PatchSet.id(changeId, num))
        .commitId(ObjectId.zeroId())
        .uploader(Account.id(1234))
        .realUploader(Account.id(5678))
        .createdOn(TimeUtil.now())
        .build();
  }
}
