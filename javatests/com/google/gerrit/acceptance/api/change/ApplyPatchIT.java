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

import static com.google.common.truth.Truth.assertWithMessage;
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.server.restapi.change.ApplyPatch;
import java.util.stream.Collectors;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class ApplyPatchIT extends AbstractDaemonTest {

  @Test
  public void applySignleFilePatch() throws Exception {
    //    TestRepository<InMemoryRepository> project1 = cloneProject(Project.nameKey(project1Name));
    ApplyPatchInput in = new ApplyPatchInput();
    in.destinationBranch = "destBranch";
    in.message = "Applying patch";
    String head = getHead(repo(), HEAD).name();
    createBranchWithRevision(BranchNameKey.create(project, "destBranch"), head);
//    gApi.projects().name(project.get()).branch(in.destinationBranch).create(new BranchInput());
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    in.patch = createDiffStr();

    ChangeInfo result = gApi.changes().create(new ChangeInput(project.get(), "master", "msg"))
        .applyPatch(in);
    assertThat(result).isNotNull();
//    DiffInfo resDiff = gApi.changes()
//        .id(result.id)
//        .revision(result.currentRevision).file("a_new_file.txt").diff();

    assertWithMessage("Original diff: \n%s\n\n~~~~~\n\nOutputDiff: \n%s", in.patch, in.patch)
        .that(true).isFalse();
  }

  private String createDiffStr() throws Exception {
    String fileName = "a_new_file.txt";
    String fileContent = "First line\nSecond line\n";
    PushOneCommit.Result result = createChange("Add a file", fileName, fileContent);
    return
        gApi.changes()
            .id(result.getChangeId())
            .revision(result.getCommit().name()).patch().asString();
//            .file(fileName)
//            .diff();
  }
}
