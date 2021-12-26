// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.server.restapi.project.FilesInCommitCollection;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

/** Test class for {@link FilesInCommitCollection}. */
public class FilesInCommitIT extends AbstractDaemonTest {
  private String changeId;

  @Before
  public void setUp() throws Exception {
    baseConfig.setString("cache", "git_file_diff", "timeout", "1 minute");

    ObjectId headCommit = testRepo.getRepository().resolve("HEAD");
    addCommit(
        headCommit,
        ImmutableMap.of("file_1.txt", "file 1 content", "file_2.txt", "file 2 content"));

    Result result = createEmptyChange();
    changeId = result.getChangeId();
  }

  @Test
  public void listFilesForSingleParentCommit() throws Exception {
    gApi.changes()
        .id(changeId)
        .edit()
        .modifyFile("a_new_file.txt", RawInputUtil.create("Line 1\nLine 2\nLine 3"));
    gApi.changes().id(changeId).edit().deleteFile("file_1.txt");
    gApi.changes().id(changeId).edit().publish();

    String lastCommitId = gApi.changes().id(changeId).get().currentRevision;

    // When parentNum is 0, the diff is performed against the default base, i.e. the single parent
    // in this case.
    Map<String, FileInfo> changedFiles =
        gApi.projects().name(project.get()).commit(lastCommitId).files(0);

    assertThat(changedFiles.keySet())
        .containsExactly("/COMMIT_MSG", "a_new_file.txt", "file_1.txt");
  }

  @Test
  public void listFilesForMergeCommitAgainstParent1() throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master", "my_file.txt");

    String changeId = result.getChangeId();
    addModifiedPatchSet(changeId, "my_file.txt", content -> content.concat("Line I\nLine II\n"));

    String lastCommitId = gApi.changes().id(changeId).get().currentRevision;

    // Diffing against the first parent.
    Map<String, FileInfo> changedFiles =
        gApi.projects().name(project.get()).commit(lastCommitId).files(1);

    assertThat(changedFiles.keySet())
        .containsExactly(
            "/COMMIT_MSG",
            "/MERGE_LIST",
            "bar", // file bar is coming from parent two
            "my_file.txt");
  }

  @Test
  public void listFilesForMergeCommitAgainstDefaultParent() throws Exception {
    PushOneCommit.Result result = createMergeCommitChange("refs/for/master", "my_file.txt");

    String changeId = result.getChangeId();
    addModifiedPatchSet(changeId, "my_file.txt", content -> content.concat("Line I\nLine II\n"));

    String lastCommitId = gApi.changes().id(changeId).get().currentRevision;

    // When parentNum is 0, the diff is performed against the default base. In this case, the
    // auto-merge commit.
    Map<String, FileInfo> changedFiles =
        gApi.projects().name(project.get()).commit(lastCommitId).files(0);

    assertThat(changedFiles.keySet())
        .containsExactly(
            "/COMMIT_MSG",
            "/MERGE_LIST",
            "bar", // file bar is coming from parent two
            "my_file.txt");
  }

  private void addModifiedPatchSet(
      String changeId, String filePath, Function<String, String> contentModification)
      throws Exception {
    try (BinaryResult content = gApi.changes().id(changeId).current().file(filePath).content()) {
      String newContent = contentModification.apply(content.asString());
      gApi.changes().id(changeId).edit().modifyFile(filePath, RawInputUtil.create(newContent));
    }
    gApi.changes().id(changeId).edit().publish();
  }

  private ObjectId addCommit(ObjectId parentCommit, ImmutableMap<String, String> files)
      throws Exception {
    testRepo.reset(parentCommit);
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Adjust files of repo", files);
    PushOneCommit.Result result = push.to("refs/for/master");
    return result.getCommit();
  }

  private Result createEmptyChange() throws Exception {
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Test change", ImmutableMap.of());
    return push.to("refs/for/master");
  }
}
