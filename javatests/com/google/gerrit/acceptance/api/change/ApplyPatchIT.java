// Copyright (C) 2022 The Android Open Source Project
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

import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import org.junit.Test;

public class ApplyPatchIT extends AbstractDaemonTest {

  private static final String ADDED_FILE_NAME = "a_new_file.txt";
  private static final String ADDED_FILE_CONTENT = "First added line\nSecond added line\n";


  @Test
  public void applySignleFilePatch() throws Exception {
    //    TestRepository<InMemoryRepository> project1 = cloneProject(Project.nameKey(project1Name));
    ApplyPatchInput in = new ApplyPatchInput();
//    in.destinationBranch = "destBranch";
    in.message = "Applying patch";
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "destBranch"), head);
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    in.patch = createDiffStr();

    ChangeInfo result = gApi.changes().create(new ChangeInput(project.get(), "master", "msg"))
        .applyPatch(in);

    DiffInfo diff = gApi.changes()
        .id(result.id)
        .current()
        .file(ADDED_FILE_NAME)
        .diff();
    assertDiffForNewFile(diff, null, ADDED_FILE_NAME, ADDED_FILE_CONTENT);
  }

  private String createDiffStr() throws Exception {
    PushOneCommit.Result result = createChange("Add a file", ADDED_FILE_NAME, ADDED_FILE_CONTENT);
    return
        gApi.changes()
            .id(result.getChangeId())
            .revision(result.getCommit().name()).patch().asString();
  }
}
